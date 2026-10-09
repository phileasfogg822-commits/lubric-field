package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.repository.AccessControlRepository
import com.lubrcapp.lubrcapp.core.repository.AuthorizationSnapshot
import com.lubrcapp.lubrcapp.core.repository.BusinessMember
import com.lubrcapp.lubrcapp.core.repository.BusinessRole
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessRoleRequest
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessUserRequest
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.store.db.DatabaseTransactionRunner
import com.lubrcapp.lubrcapp.core.store.db.dao.AccessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SettingsDao
import com.lubrcapp.lubrcapp.core.store.db.dao.UserDao
import com.lubrcapp.lubrcapp.core.store.db.entity.PermissionEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.RoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.RolePermissionEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessRoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.IdGenerator
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.common.roleId
import com.lubrcapp.lubrcapp.domain.common.userBusinessId
import com.lubrcapp.lubrcapp.domain.common.userId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomAccessControlRepository @Inject constructor(
    private val userDao: UserDao,
    private val accessDao: AccessDao,
    private val settingsDao: SettingsDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val deviceIdentityProvider: DeviceIdentityProvider,
    private val idGenerator: IdGenerator,
) : AccessControlRepository {

    override fun observeAuthorization(
        context: TenantContext,
    ): Flow<AuthorizationSnapshot> =
        combine(
            accessDao.observeMemberships(
                context.tenantId.value,
                context.businessId.value,
            ),
            accessDao.observeRoleIdsForUser(
                context.tenantId.value,
                context.businessId.value,
                context.userId.value,
            ),
            accessDao.observePermissionKeys(
                context.tenantId.value,
                context.businessId.value,
                context.userId.value,
            ),
        ) { memberships, roleIds, permissionKeys ->
            val membership = memberships.firstOrNull {
                it.userId == context.userId.value && it.isActive
            }
            if (membership == null) {
                deniedSnapshot(context)
            } else {
                AuthorizationSnapshot(
                    context = context,
                    membershipId = UserBusinessId(membership.id),
                    roleIds = roleIds.mapTo(linkedSetOf(), ::RoleId),
                    capabilities = permissionKeys.mapNotNullTo(linkedSetOf()) {
                        parseCapability(it)
                    },
                )
            }
        }

    override suspend fun authorization(
        context: TenantContext,
    ): AuthorizationSnapshot {
        val membership = accessDao.getMembership(
            context.tenantId.value,
            context.businessId.value,
            context.userId.value,
        ) ?: return deniedSnapshot(context)

        val roleIds = accessDao.getRoleIds(
            context.tenantId.value,
            context.businessId.value,
            membership.id,
        ).mapTo(linkedSetOf(), ::RoleId)

        val capabilities = accessDao.getPermissionKeys(
            context.tenantId.value,
            context.businessId.value,
            context.userId.value,
        ).mapNotNullTo(linkedSetOf()) { parseCapability(it) }

        return AuthorizationSnapshot(
            context = context,
            membershipId = UserBusinessId(membership.id),
            roleIds = roleIds,
            capabilities = capabilities,
        )
    }

    override suspend fun ensureOwnerAccess(
        tenantId: TenantId,
        businessId: BusinessId,
    ) {
        val ownerId = settingsDao.get(
            tenantId.value,
            businessId.value,
            BusinessSetupSettingKeys.OWNER_USER_ID,
        )?.value?.takeIf(String::isNotBlank) ?: return

        val membership = accessDao.getMembership(
            tenantId.value,
            businessId.value,
            ownerId,
        ) ?: return

        val deviceId = deviceIdentityProvider.getOrCreate()
        val now = Instant.now().toEpochMilli()
        val ownerRoleId = stableOwnerRoleId(tenantId, businessId)

        transactionRunner.run {
            seedCapabilityCatalog()

            accessDao.upsertRole(
                RoleEntity(
                    id = ownerRoleId.value,
                    tenantId = tenantId.value,
                    businessId = businessId.value,
                    name = OWNER_ROLE_DISPLAY_NAME,
                    isSystemTemplate = true,
                    isActive = true,
                    createdAt = now,
                    createdBy = ownerId,
                    updatedAt = now,
                    updatedBy = ownerId,
                    deviceId = deviceId.value,
                    syncStatus = SyncStatus.LOCAL_ONLY.name,
                )
            )

            accessDao.clearRolePermissions(
                tenantId.value,
                businessId.value,
                ownerRoleId.value,
            )
            for (capability in Capabilities.ALL) {
                accessDao.addRolePermission(
                    RolePermissionEntity(
                        tenantId = tenantId.value,
                        businessId = businessId.value,
                        roleId = ownerRoleId.value,
                        permissionId = stablePermissionId(capability),
                    )
                )
            }

            accessDao.addUserBusinessRole(
                UserBusinessRoleEntity(
                    tenantId = tenantId.value,
                    businessId = businessId.value,
                    userBusinessId = membership.id,
                    roleId = ownerRoleId.value,
                )
            )
        }
    }

    override fun observeMembers(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessMember>> =
        accessDao.observeMemberships(tenantId.value, businessId.value)
            .map { memberships ->
                memberships.mapNotNull { membership ->
                    val user = userDao.getById(membership.userId)
                        ?: return@mapNotNull null
                    BusinessMember(
                        membershipId = UserBusinessId(membership.id),
                        userId = UserId(user.id),
                        name = user.name,
                        phone = user.phone,
                        email = user.email,
                        active = membership.isActive && user.isActive,
                        roleIds = accessDao.getRoleIds(
                            tenantId.value,
                            businessId.value,
                            membership.id,
                        ).mapTo(linkedSetOf(), ::RoleId),
                    )
                }
            }

    override fun observeRoles(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessRole>> =
        accessDao.observeRoles(tenantId.value, businessId.value)
            .map { roles ->
                roles.map { role ->
                    BusinessRole(
                        roleId = RoleId(role.id),
                        name = role.name,
                        systemTemplate = role.isSystemTemplate,
                        active = role.isActive,
                        capabilities = accessDao.getRolePermissionKeys(
                            tenantId.value,
                            businessId.value,
                            role.id,
                        ).mapNotNullTo(linkedSetOf()) { parseCapability(it) },
                    )
                }
            }

    override suspend fun createBusinessUser(
        actor: TenantContext,
        request: CreateBusinessUserRequest,
    ): BusinessMember {
        val deviceId = deviceIdentityProvider.getOrCreate()
        val now = Instant.now().toEpochMilli()
        val userId = idGenerator.userId()
        val membershipId = idGenerator.userBusinessId()

        transactionRunner.run {
            userDao.upsert(
                UserEntity(
                    id = userId.value,
                    name = request.name.trim(),
                    phone = request.phone.trim(),
                    email = request.email?.trim()?.takeIf(String::isNotEmpty),
                    createdAt = now,
                    createdBy = actor.userId.value,
                    updatedAt = now,
                    updatedBy = actor.userId.value,
                    deviceId = deviceId.value,
                    syncStatus = SyncStatus.LOCAL_ONLY.name,
                )
            )
            accessDao.upsertUserBusiness(
                UserBusinessEntity(
                    id = membershipId.value,
                    tenantId = actor.tenantId.value,
                    businessId = actor.businessId.value,
                    userId = userId.value,
                    createdAt = now,
                    createdBy = actor.userId.value,
                    updatedAt = now,
                    updatedBy = actor.userId.value,
                    deviceId = deviceId.value,
                    syncStatus = SyncStatus.LOCAL_ONLY.name,
                )
            )
        }

        return BusinessMember(
            membershipId = membershipId,
            userId = userId,
            name = request.name.trim(),
            phone = request.phone.trim(),
            email = request.email?.trim()?.takeIf(String::isNotEmpty),
            active = true,
            roleIds = emptySet(),
        )
    }

    override suspend fun setMembershipActive(
        actor: TenantContext,
        membershipId: UserBusinessId,
        active: Boolean,
    ) {
        val membership = requireMembership(actor, membershipId)
        val ownerUserId = settingsDao.get(
            actor.tenantId.value,
            actor.businessId.value,
            BusinessSetupSettingKeys.OWNER_USER_ID,
        )?.value

        require(active || membership.userId != ownerUserId) {
            "The initial business owner membership cannot be deactivated"
        }

        val changed = accessDao.setMembershipActive(
            tenantId = actor.tenantId.value,
            businessId = actor.businessId.value,
            membershipId = membershipId.value,
            active = active,
            updatedAt = Instant.now().toEpochMilli(),
            updatedBy = actor.userId.value,
        )
        check(changed == 1) { "Membership update failed" }
    }

    override suspend fun createRole(
        actor: TenantContext,
        request: CreateBusinessRoleRequest,
    ): BusinessRole {
        validateCapabilities(request.capabilities)
        val deviceId = deviceIdentityProvider.getOrCreate()
        val roleId = idGenerator.roleId()
        val now = Instant.now().toEpochMilli()

        transactionRunner.run {
            seedCapabilityCatalog()
            accessDao.upsertRole(
                RoleEntity(
                    id = roleId.value,
                    tenantId = actor.tenantId.value,
                    businessId = actor.businessId.value,
                    name = request.name.trim(),
                    isSystemTemplate = false,
                    isActive = true,
                    createdAt = now,
                    createdBy = actor.userId.value,
                    updatedAt = now,
                    updatedBy = actor.userId.value,
                    deviceId = deviceId.value,
                    syncStatus = SyncStatus.LOCAL_ONLY.name,
                )
            )
            replaceRoleCapabilities(actor, roleId, request.capabilities)
        }

        return BusinessRole(
            roleId = roleId,
            name = request.name.trim(),
            systemTemplate = false,
            active = true,
            capabilities = request.capabilities,
        )
    }

    override suspend fun setRoleCapabilities(
        actor: TenantContext,
        roleId: RoleId,
        capabilities: Set<CapabilityKey>,
    ) {
        validateCapabilities(capabilities)
        val role = requireRole(actor, roleId)
        require(!role.isSystemTemplate) {
            "System role capabilities cannot be modified"
        }

        transactionRunner.run {
            seedCapabilityCatalog()
            replaceRoleCapabilities(actor, roleId, capabilities)
        }
    }

    override suspend fun assignRoles(
        actor: TenantContext,
        membershipId: UserBusinessId,
        roleIds: Set<RoleId>,
    ) {
        requireMembership(actor, membershipId)

        val roles = roleIds.map { roleId -> requireRole(actor, roleId) }
        require(roles.none { it.isSystemTemplate }) {
            "System roles cannot be manually assigned"
        }

        transactionRunner.run {
            accessDao.clearUserBusinessRoles(
                actor.tenantId.value,
                actor.businessId.value,
                membershipId.value,
            )
            for (roleId in roleIds) {
                accessDao.addUserBusinessRole(
                    UserBusinessRoleEntity(
                        tenantId = actor.tenantId.value,
                        businessId = actor.businessId.value,
                        userBusinessId = membershipId.value,
                        roleId = roleId.value,
                    )
                )
            }
        }
    }

    private suspend fun seedCapabilityCatalog() {
        for (definition in Capabilities.DEFINITIONS) {
            accessDao.upsertPermission(
                PermissionEntity(
                    id = stablePermissionId(definition.key),
                    permissionKey = definition.key.value,
                    description = definition.description,
                )
            )
        }
    }

    private suspend fun replaceRoleCapabilities(
        actor: TenantContext,
        roleId: RoleId,
        capabilities: Set<CapabilityKey>,
    ) {
        accessDao.clearRolePermissions(
            actor.tenantId.value,
            actor.businessId.value,
            roleId.value,
        )
        for (capability in capabilities) {
            accessDao.addRolePermission(
                RolePermissionEntity(
                    tenantId = actor.tenantId.value,
                    businessId = actor.businessId.value,
                    roleId = roleId.value,
                    permissionId = stablePermissionId(capability),
                )
            )
        }
    }

    private suspend fun requireMembership(
        actor: TenantContext,
        membershipId: UserBusinessId,
    ): UserBusinessEntity =
        requireNotNull(
            accessDao.getMembershipById(
                actor.tenantId.value,
                actor.businessId.value,
                membershipId.value,
            )
        ) { "Membership is not part of the active business" }

    private suspend fun requireRole(
        actor: TenantContext,
        roleId: RoleId,
    ): RoleEntity =
        requireNotNull(
            accessDao.getRole(
                actor.tenantId.value,
                actor.businessId.value,
                roleId.value,
            )
        ) { "Role is not part of the active business" }

    private fun validateCapabilities(capabilities: Set<CapabilityKey>) {
        require(Capabilities.ALL.containsAll(capabilities)) {
            "Role contains capability keys outside the platform catalog"
        }
    }

    private fun deniedSnapshot(context: TenantContext) =
        AuthorizationSnapshot(
            context = context,
            membershipId = null,
            roleIds = emptySet(),
            capabilities = emptySet(),
        )

    private fun parseCapability(value: String): CapabilityKey? =
        runCatching { CapabilityKey(value) }
            .getOrNull()
            ?.takeIf { it in Capabilities.ALL }

    private fun stablePermissionId(capability: CapabilityKey): String =
        stableId("businessos:capability:${capability.value}")

    private fun stableOwnerRoleId(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RoleId = RoleId(
        stableId("businessos:owner-role:${tenantId.value}:${businessId.value}")
    )

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private companion object {
        const val OWNER_ROLE_DISPLAY_NAME = "Owner"
    }
}
