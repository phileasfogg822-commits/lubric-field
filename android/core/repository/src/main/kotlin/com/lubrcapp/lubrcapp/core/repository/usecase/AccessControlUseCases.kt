package com.lubrcapp.lubrcapp.core.repository.usecase

import com.lubrcapp.lubrcapp.core.repository.AccessControlRepository
import com.lubrcapp.lubrcapp.core.repository.AuthorizationDeniedException
import com.lubrcapp.lubrcapp.core.repository.AuthorizationSnapshot
import com.lubrcapp.lubrcapp.core.repository.BusinessMember
import com.lubrcapp.lubrcapp.core.repository.BusinessRole
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessRoleRequest
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessUserRequest
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class AuthorizationService @Inject constructor(
    private val repository: AccessControlRepository,
) {
    suspend fun snapshot(context: TenantContext): AuthorizationSnapshot {
        repository.ensureOwnerAccess(context.tenantId, context.businessId)
        return repository.authorization(context)
    }

    suspend fun allows(
        context: TenantContext,
        capability: CapabilityKey,
    ): Boolean {
        repository.ensureOwnerAccess(context.tenantId, context.businessId)
        return repository.authorization(context).allows(capability)
    }

    suspend fun require(
        context: TenantContext,
        capability: CapabilityKey,
    ) {
        if (!allows(context, capability)) {
            throw AuthorizationDeniedException(context, capability)
        }
    }
}

class EnsureOwnerAccessUseCase @Inject constructor(
    private val repository: AccessControlRepository,
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
    ) = repository.ensureOwnerAccess(tenantId, businessId)
}

class ObserveBusinessMembersUseCase @Inject constructor(
    private val repository: AccessControlRepository,
) {
    operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessMember>> = repository.observeMembers(tenantId, businessId)
}

class ObserveBusinessRolesUseCase @Inject constructor(
    private val repository: AccessControlRepository,
) {
    operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessRole>> = repository.observeRoles(tenantId, businessId)
}

class CreateBusinessUserUseCase @Inject constructor(
    private val repository: AccessControlRepository,
    private val authorization: AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        request: CreateBusinessUserRequest,
    ): BusinessMember {
        authorization.require(actor, Capabilities.USERS_MANAGE)
        return repository.createBusinessUser(actor, request)
    }
}

class SetMembershipActiveUseCase @Inject constructor(
    private val repository: AccessControlRepository,
    private val authorization: AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        membershipId: UserBusinessId,
        active: Boolean,
    ) {
        authorization.require(actor, Capabilities.USERS_MANAGE)
        repository.setMembershipActive(actor, membershipId, active)
    }
}

class CreateBusinessRoleUseCase @Inject constructor(
    private val repository: AccessControlRepository,
    private val authorization: AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        request: CreateBusinessRoleRequest,
    ): BusinessRole {
        authorization.require(actor, Capabilities.ROLES_MANAGE)
        return repository.createRole(actor, request)
    }
}

class SetRoleCapabilitiesUseCase @Inject constructor(
    private val repository: AccessControlRepository,
    private val authorization: AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        roleId: RoleId,
        capabilities: Set<CapabilityKey>,
    ) {
        authorization.require(actor, Capabilities.ROLES_MANAGE)
        repository.setRoleCapabilities(actor, roleId, capabilities)
    }
}

class AssignBusinessRolesUseCase @Inject constructor(
    private val repository: AccessControlRepository,
    private val authorization: AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        membershipId: UserBusinessId,
        roleIds: Set<RoleId>,
    ) {
        authorization.require(actor, Capabilities.ROLES_MANAGE)
        repository.assignRoles(actor, membershipId, roleIds)
    }
}
