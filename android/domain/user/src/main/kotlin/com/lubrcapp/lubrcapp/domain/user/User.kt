package com.lubrcapp.lubrcapp.domain.user

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.PermissionId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant

data class User(
    val id: UserId,
    val name: String,
    val phone: String = "",
    val email: String? = null,
    val isActive: Boolean = true,
    val lastLoginAt: Instant? = null,
    val metadata: EntityMetadata,
)

data class UserBusiness(
    val id: UserBusinessId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val userId: UserId,
    val roleIds: Set<RoleId> = emptySet(),
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)

data class Role(
    val id: RoleId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val name: String,
    val permissionIds: Set<PermissionId> = emptySet(),
    val isSystemTemplate: Boolean = false,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)

data class Permission(
    val id: PermissionId,
    val key: String,
    val description: String = "",
) {
    init {
        require(key.isNotBlank()) { "Permission key cannot be blank" }
    }
}
