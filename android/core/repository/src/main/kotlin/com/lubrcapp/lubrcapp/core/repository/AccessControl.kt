package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import kotlinx.coroutines.flow.Flow

data class AuthorizationSnapshot(
    val context: TenantContext,
    val membershipId: UserBusinessId?,
    val roleIds: Set<RoleId>,
    val capabilities: Set<CapabilityKey>,
) {
    fun allows(capability: CapabilityKey): Boolean =
        membershipId != null && capability in capabilities
}

data class AuthorizationContract(
    val tenantId: String,
    val businessId: String,
    val userId: String,
    val membershipId: String?,
    val roleIds: Set<String>,
    val capabilityKeys: Set<String>,
)

fun AuthorizationSnapshot.toContract(): AuthorizationContract =
    AuthorizationContract(
        tenantId = context.tenantId.value,
        businessId = context.businessId.value,
        userId = context.userId.value,
        membershipId = membershipId?.value,
        roleIds = roleIds.mapTo(linkedSetOf()) { it.value },
        capabilityKeys = capabilities.mapTo(linkedSetOf()) { it.value },
    )

data class BusinessMember(
    val membershipId: UserBusinessId,
    val userId: UserId,
    val name: String,
    val phone: String,
    val email: String?,
    val active: Boolean,
    val roleIds: Set<RoleId>,
)

data class BusinessRole(
    val roleId: RoleId,
    val name: String,
    val systemTemplate: Boolean,
    val active: Boolean,
    val capabilities: Set<CapabilityKey>,
)

data class CreateBusinessUserRequest(
    val name: String,
    val phone: String = "",
    val email: String? = null,
) {
    init {
        require(name.isNotBlank()) { "User name cannot be blank" }
    }
}

data class CreateBusinessRoleRequest(
    val name: String,
    val capabilities: Set<CapabilityKey> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Role name cannot be blank" }
    }
}

class AuthorizationDeniedException(
    val context: TenantContext,
    val requiredCapability: CapabilityKey,
) : IllegalStateException(
    "Capability ${requiredCapability.value} is required for business ${context.businessId.value}"
)

interface AccessControlRepository {
    fun observeAuthorization(context: TenantContext): Flow<AuthorizationSnapshot>
    suspend fun authorization(context: TenantContext): AuthorizationSnapshot

    suspend fun ensureOwnerAccess(
        tenantId: TenantId,
        businessId: BusinessId,
    )

    fun observeMembers(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessMember>>

    fun observeRoles(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessRole>>

    suspend fun createBusinessUser(
        actor: TenantContext,
        request: CreateBusinessUserRequest,
    ): BusinessMember

    suspend fun setMembershipActive(
        actor: TenantContext,
        membershipId: UserBusinessId,
        active: Boolean,
    )

    suspend fun createRole(
        actor: TenantContext,
        request: CreateBusinessRoleRequest,
    ): BusinessRole

    suspend fun setRoleCapabilities(
        actor: TenantContext,
        roleId: RoleId,
        capabilities: Set<CapabilityKey>,
    )

    suspend fun assignRoles(
        actor: TenantContext,
        membershipId: UserBusinessId,
        roleIds: Set<RoleId>,
    )
}
