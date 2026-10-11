package com.lubrcapp.lubrcapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lubrcapp.lubrcapp.core.auth.AuthSessionController
import com.lubrcapp.lubrcapp.core.auth.DeviceSecurityPolicy
import com.lubrcapp.lubrcapp.core.auth.SecurityPolicyStore
import com.lubrcapp.lubrcapp.core.auth.SessionSecurityInfo
import com.lubrcapp.lubrcapp.core.repository.AuditEvent
import com.lubrcapp.lubrcapp.core.repository.BusinessMember
import com.lubrcapp.lubrcapp.core.repository.BusinessRole
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessRoleRequest
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessUserRequest
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.repository.usecase.AssignBusinessRolesUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.AuthorizationService
import com.lubrcapp.lubrcapp.core.repository.usecase.CreateBusinessRoleUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.CreateBusinessUserUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.EnsureOwnerAccessUseCase
import com.lubrcapp.lubrcapp.core.repository.ObserveAuditEventsUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveBusinessMembersUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveBusinessRolesUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.SetMembershipActiveUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.SetRoleActiveUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.SetRoleCapabilitiesUseCase
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class AccessStreams(
    val members: List<BusinessMember>,
    val roles: List<BusinessRole>,
    val auditEvents: List<AuditEvent>,
    val securityPolicy: DeviceSecurityPolicy,
)

data class AccessControlUiState(
    val members: List<BusinessMember> = emptyList(),
    val roles: List<BusinessRole> = emptyList(),
    val auditEvents: List<AuditEvent> = emptyList(),
    val securityPolicy: DeviceSecurityPolicy = DeviceSecurityPolicy(),
    val sessionInfo: SessionSecurityInfo? = null,
    val deviceId: String? = null,
    val canManageUsers: Boolean = false,
    val canManageRoles: Boolean = false,
    val canViewAudit: Boolean = false,
    val canManageSecurity: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class AccessControlViewModel @Inject constructor(
    private val ensureOwnerAccess: EnsureOwnerAccessUseCase,
    private val observeMembers: ObserveBusinessMembersUseCase,
    private val observeRoles: ObserveBusinessRolesUseCase,
    private val observeAuditEvents: ObserveAuditEventsUseCase,
    private val securityPolicyStore: SecurityPolicyStore,
    private val sessionController: AuthSessionController,
    private val deviceIdentityProvider: DeviceIdentityProvider,
    private val createUserUseCase: CreateBusinessUserUseCase,
    private val setMembershipActiveUseCase: SetMembershipActiveUseCase,
    private val createRoleUseCase: CreateBusinessRoleUseCase,
    private val setRoleActiveUseCase: SetRoleActiveUseCase,
    private val setRoleCapabilitiesUseCase: SetRoleCapabilitiesUseCase,
    private val assignRolesUseCase: AssignBusinessRolesUseCase,
    private val authorization: AuthorizationService,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AccessControlUiState())
    val state: StateFlow<AccessControlUiState> = mutableState.asStateFlow()

    private var context: TenantContext? = null
    private var observationJob: Job? = null

    fun start(context: TenantContext) {
        if (this.context == context && observationJob?.isActive == true) return
        this.context = context
        observationJob?.cancel()

        observationJob = viewModelScope.launch {
            val snapshot = runCatching {
                ensureOwnerAccess(context.tenantId, context.businessId)
                authorization.snapshot(context)
            }.onFailure(::showError).getOrNull()

            val canViewAudit = snapshot?.allows(Capabilities.AUDIT_VIEW) == true
            val canManageSecurity = snapshot?.allows(Capabilities.SETTINGS_MANAGE) == true
            runCatching { securityPolicyStore.initialize() }
                .onFailure(::showError)
            val sessionInfo = runCatching { sessionController.securityInfo() }
                .onFailure(::showError)
                .getOrNull()
            val deviceId = runCatching { deviceIdentityProvider.getOrCreate().value }
                .onFailure(::showError)
                .getOrNull()

            mutableState.value = mutableState.value.copy(
                canManageUsers = snapshot?.allows(Capabilities.USERS_MANAGE) == true,
                canManageRoles = snapshot?.allows(Capabilities.ROLES_MANAGE) == true,
                canViewAudit = canViewAudit,
                canManageSecurity = canManageSecurity,
                sessionInfo = sessionInfo,
                deviceId = deviceId,
            )

            val auditFlow = if (canViewAudit) {
                runCatching { observeAuditEvents(context, limit = 100) }
                    .onFailure(::showError)
                    .getOrElse { flowOf(emptyList()) }
            } else {
                flowOf(emptyList())
            }

            combine(
                observeMembers(context.tenantId, context.businessId),
                observeRoles(context.tenantId, context.businessId),
                auditFlow,
                securityPolicyStore.policy,
            ) { members, roles, auditEvents, securityPolicy ->
                AccessStreams(members, roles, auditEvents, securityPolicy)
            }.collect { streams ->
                mutableState.value = mutableState.value.copy(
                    members = streams.members,
                    roles = streams.roles,
                    auditEvents = streams.auditEvents,
                    securityPolicy = streams.securityPolicy,
                    isLoading = false,
                )
            }
        }
    }

    fun createUser(
        name: String,
        phone: String = "",
        email: String? = null,
    ) = withContextAction { actor ->
        createUserUseCase(
            actor,
            CreateBusinessUserRequest(name, phone, email),
        )
    }

    fun setMemberActive(
        membershipId: UserBusinessId,
        active: Boolean,
    ) = withContextAction { actor ->
        setMembershipActiveUseCase(actor, membershipId, active)
    }

    fun createRole(
        name: String,
        capabilities: Set<CapabilityKey>,
    ) = withContextAction { actor ->
        createRoleUseCase(
            actor,
            CreateBusinessRoleRequest(name, capabilities),
        )
    }

    fun setRoleActive(
        roleId: RoleId,
        active: Boolean,
    ) = withContextAction { actor ->
        setRoleActiveUseCase(actor, roleId, active)
    }

    fun setRoleCapabilities(
        roleId: RoleId,
        capabilities: Set<CapabilityKey>,
    ) = withContextAction { actor ->
        setRoleCapabilitiesUseCase(actor, roleId, capabilities)
    }

    fun assignRoles(
        membershipId: UserBusinessId,
        roleIds: Set<RoleId>,
    ) = withContextAction { actor ->
        assignRolesUseCase(actor, membershipId, roleIds)
    }

    fun setBlockScreenshots(enabled: Boolean) {
        if (!mutableState.value.canManageSecurity) return
        viewModelScope.launch {
            runCatching { securityPolicyStore.setBlockScreenshots(enabled) }
                .onFailure(::showError)
        }
    }

    fun setLockOnBackground(enabled: Boolean) {
        if (!mutableState.value.canManageSecurity) return
        viewModelScope.launch {
            runCatching { securityPolicyStore.setLockOnBackground(enabled) }
                .onFailure(::showError)
        }
    }

    private fun withContextAction(
        action: suspend (TenantContext) -> Unit,
    ) {
        val actor = context ?: return
        viewModelScope.launch {
            runCatching { action(actor) }
                .onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            isLoading = false,
            errorMessage = error.message ?: "Access-control operation failed",
        )
    }
}
