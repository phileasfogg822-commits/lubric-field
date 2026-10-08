package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.repository.BusinessFeatureFlag
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.core.repository.SyncOperationKind
import com.lubrcapp.lubrcapp.core.repository.SyncQueueItem
import com.lubrcapp.lubrcapp.core.repository.SyncQueueSummary
import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SyncQueueLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserLocalDataSource
import com.lubrcapp.lubrcapp.core.store.db.DatabaseTransactionRunner
import com.lubrcapp.lubrcapp.core.store.db.dao.AccessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.BusinessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.FeatureFlagDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SettingsDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SyncQueueDao
import com.lubrcapp.lubrcapp.core.store.db.dao.TenantDao
import com.lubrcapp.lubrcapp.core.store.db.dao.UserDao
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.FeatureFlagEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SyncQueueEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessRoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.business.Tenant
import com.lubrcapp.lubrcapp.domain.common.AuditMetadata
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.FeatureFlagId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.SettingId
import com.lubrcapp.lubrcapp.domain.common.SoftDeleteMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncOperationId
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.User
import com.lubrcapp.lubrcapp.domain.user.UserBusiness
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun metadata(
    createdAt: Long,
    createdBy: String?,
    updatedAt: Long,
    updatedBy: String?,
    deletedAt: Long?,
    deletedBy: String?,
    deviceId: String,
    serverId: String?,
    revision: Long,
    syncStatus: String,
): EntityMetadata = EntityMetadata(
    audit = AuditMetadata(
        createdAt = Instant.ofEpochMilli(createdAt),
        createdBy = createdBy?.let(::UserId),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        updatedBy = updatedBy?.let(::UserId),
    ),
    deletion = SoftDeleteMetadata(
        deletedAt = deletedAt?.let(Instant::ofEpochMilli),
        deletedBy = deletedBy?.let(::UserId),
    ),
    sync = SyncMetadata(
        deviceId = DeviceId(deviceId),
        serverId = serverId,
        revision = revision,
        status = SyncStatus.valueOf(syncStatus),
    ),
)

private fun TenantEntity.toDomain() = Tenant(
    id = TenantId(id),
    name = name,
    isActive = isActive,
    metadata = metadata(
        createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy,
        deviceId, serverId, revision, syncStatus,
    ),
)

private fun Tenant.toEntity() = TenantEntity(
    id = id.value,
    name = name,
    isActive = isActive,
    createdAt = metadata.audit.createdAt.toEpochMilli(),
    createdBy = metadata.audit.createdBy?.value,
    updatedAt = metadata.audit.updatedAt.toEpochMilli(),
    updatedBy = metadata.audit.updatedBy?.value,
    deletedAt = metadata.deletion.deletedAt?.toEpochMilli(),
    deletedBy = metadata.deletion.deletedBy?.value,
    deviceId = metadata.sync.deviceId.value,
    serverId = metadata.sync.serverId,
    revision = metadata.sync.revision,
    syncStatus = metadata.sync.status.name,
)

private fun BusinessEntity.toDomain() = Business(
    id = BusinessId(id),
    tenantId = TenantId(tenantId),
    name = name,
    businessType = businessType,
    phone = phone,
    email = email,
    address = address,
    country = country,
    postalCode = postalCode,
    website = website,
    taxNumber = taxNumber,
    currency = CurrencyCode.of(currencyCode),
    timezone = ZoneId.of(timezoneId),
    inviteCode = inviteCode,
    isActive = isActive,
    metadata = metadata(
        createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy,
        deviceId, serverId, revision, syncStatus,
    ),
)

private fun Business.toEntity() = BusinessEntity(
    id = id.value,
    tenantId = tenantId.value,
    name = name,
    businessType = businessType,
    phone = phone,
    email = email,
    address = address,
    country = country,
    postalCode = postalCode,
    website = website,
    taxNumber = taxNumber,
    currencyCode = currency.value,
    timezoneId = timezone.id,
    inviteCode = inviteCode,
    isActive = isActive,
    createdAt = metadata.audit.createdAt.toEpochMilli(),
    createdBy = metadata.audit.createdBy?.value,
    updatedAt = metadata.audit.updatedAt.toEpochMilli(),
    updatedBy = metadata.audit.updatedBy?.value,
    deletedAt = metadata.deletion.deletedAt?.toEpochMilli(),
    deletedBy = metadata.deletion.deletedBy?.value,
    deviceId = metadata.sync.deviceId.value,
    serverId = metadata.sync.serverId,
    revision = metadata.sync.revision,
    syncStatus = metadata.sync.status.name,
)

private fun UserEntity.toDomain() = User(
    id = UserId(id),
    name = name,
    phone = phone,
    email = email,
    isActive = isActive,
    lastLoginAt = lastLoginAt?.let(Instant::ofEpochMilli),
    metadata = metadata(
        createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy,
        deviceId, serverId, revision, syncStatus,
    ),
)

private fun User.toEntity() = UserEntity(
    id = id.value,
    name = name,
    phone = phone,
    email = email,
    isActive = isActive,
    lastLoginAt = lastLoginAt?.toEpochMilli(),
    createdAt = metadata.audit.createdAt.toEpochMilli(),
    createdBy = metadata.audit.createdBy?.value,
    updatedAt = metadata.audit.updatedAt.toEpochMilli(),
    updatedBy = metadata.audit.updatedBy?.value,
    deletedAt = metadata.deletion.deletedAt?.toEpochMilli(),
    deletedBy = metadata.deletion.deletedBy?.value,
    deviceId = metadata.sync.deviceId.value,
    serverId = metadata.sync.serverId,
    revision = metadata.sync.revision,
    syncStatus = metadata.sync.status.name,
)

private fun UserBusinessEntity.toDomain(roleIds: Set<RoleId>) = UserBusiness(
    id = UserBusinessId(id),
    tenantId = TenantId(tenantId),
    businessId = BusinessId(businessId),
    userId = UserId(userId),
    roleIds = roleIds,
    isActive = isActive,
    metadata = metadata(
        createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy,
        deviceId, serverId, revision, syncStatus,
    ),
)

private fun UserBusiness.toEntity() = UserBusinessEntity(
    id = id.value,
    tenantId = tenantId.value,
    businessId = businessId.value,
    userId = userId.value,
    isActive = isActive,
    createdAt = metadata.audit.createdAt.toEpochMilli(),
    createdBy = metadata.audit.createdBy?.value,
    updatedAt = metadata.audit.updatedAt.toEpochMilli(),
    updatedBy = metadata.audit.updatedBy?.value,
    deletedAt = metadata.deletion.deletedAt?.toEpochMilli(),
    deletedBy = metadata.deletion.deletedBy?.value,
    deviceId = metadata.sync.deviceId.value,
    serverId = metadata.sync.serverId,
    revision = metadata.sync.revision,
    syncStatus = metadata.sync.status.name,
)

private fun SettingEntity.toDomain() = BusinessSetting(
    id = SettingId(id),
    tenantId = TenantId(tenantId),
    businessId = BusinessId(businessId),
    key = settingKey,
    value = value,
    valueType = valueType,
    metadata = metadata(
        createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy,
        deviceId, serverId, revision, syncStatus,
    ),
)

private fun BusinessSetting.toEntity() = SettingEntity(
    id = id.value,
    tenantId = tenantId.value,
    businessId = businessId.value,
    settingKey = key,
    value = value,
    valueType = valueType,
    createdAt = metadata.audit.createdAt.toEpochMilli(),
    createdBy = metadata.audit.createdBy?.value,
    updatedAt = metadata.audit.updatedAt.toEpochMilli(),
    updatedBy = metadata.audit.updatedBy?.value,
    deletedAt = metadata.deletion.deletedAt?.toEpochMilli(),
    deletedBy = metadata.deletion.deletedBy?.value,
    deviceId = metadata.sync.deviceId.value,
    serverId = metadata.sync.serverId,
    revision = metadata.sync.revision,
    syncStatus = metadata.sync.status.name,
)

private fun FeatureFlagEntity.toDomain() = BusinessFeatureFlag(
    id = FeatureFlagId(id),
    tenantId = TenantId(tenantId),
    businessId = BusinessId(businessId),
    key = featureKey,
    enabled = enabled,
    source = source,
    metadata = metadata(
        createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy,
        deviceId, serverId, revision, syncStatus,
    ),
)

private fun BusinessFeatureFlag.toEntity() = FeatureFlagEntity(
    id = id.value,
    tenantId = tenantId.value,
    businessId = businessId.value,
    featureKey = key,
    enabled = enabled,
    source = source,
    createdAt = metadata.audit.createdAt.toEpochMilli(),
    createdBy = metadata.audit.createdBy?.value,
    updatedAt = metadata.audit.updatedAt.toEpochMilli(),
    updatedBy = metadata.audit.updatedBy?.value,
    deletedAt = metadata.deletion.deletedAt?.toEpochMilli(),
    deletedBy = metadata.deletion.deletedBy?.value,
    deviceId = metadata.sync.deviceId.value,
    serverId = metadata.sync.serverId,
    revision = metadata.sync.revision,
    syncStatus = metadata.sync.status.name,
)

private fun SyncQueueEntity.toDomain() = SyncQueueItem(
    id = SyncOperationId(id),
    tenantId = TenantId(tenantId),
    businessId = BusinessId(businessId),
    entityType = entityType,
    entityId = entityId,
    operation = SyncOperationKind.valueOf(operation),
    payloadJson = payloadJson,
    revision = revision,
    status = SyncStatus.valueOf(status),
    attemptCount = attemptCount,
    lastError = lastError,
    nextAttemptAt = nextAttemptAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    userId = userId?.let(::UserId),
    deviceId = DeviceId(deviceId),
)

private fun SyncQueueItem.toEntity() = SyncQueueEntity(
    id = id.value,
    tenantId = tenantId.value,
    businessId = businessId.value,
    entityType = entityType,
    entityId = entityId,
    operation = operation.name,
    payloadJson = payloadJson,
    revision = revision,
    status = status.name,
    attemptCount = attemptCount,
    lastError = lastError,
    nextAttemptAt = nextAttemptAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    userId = userId?.value,
    deviceId = deviceId.value,
)

@Singleton
class RoomBusinessLocalDataSource @Inject constructor(
    private val tenantDao: TenantDao,
    private val businessDao: BusinessDao,
) : BusinessLocalDataSource {
    override fun observeBusinesses(tenantId: TenantId): Flow<List<Business>> =
        businessDao.observeByTenant(tenantId.value).map { rows -> rows.map(BusinessEntity::toDomain) }

    override suspend fun getBusiness(tenantId: TenantId, businessId: BusinessId): Business? =
        businessDao.getById(tenantId.value, businessId.value)?.toDomain()

    override suspend fun saveTenant(tenant: Tenant) = tenantDao.upsert(tenant.toEntity())
    override suspend fun saveBusiness(business: Business) = businessDao.upsert(business.toEntity())

    override suspend fun softDeleteBusiness(
        tenantId: TenantId,
        businessId: BusinessId,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean = businessDao.softDelete(
        tenantId.value, businessId.value, deletedAt.toEpochMilli(), deletedBy?.value
    ) == 1
}

@Singleton
class RoomUserLocalDataSource @Inject constructor(
    private val userDao: UserDao,
    private val accessDao: AccessDao,
    private val transactionRunner: DatabaseTransactionRunner,
) : UserLocalDataSource {
    override suspend fun getUser(userId: UserId): User? =
        userDao.getById(userId.value)?.toDomain()

    override suspend fun saveUser(user: User) = userDao.upsert(user.toEntity())

    override suspend fun saveMembership(membership: UserBusiness) {
        transactionRunner.run {
            accessDao.upsertUserBusiness(membership.toEntity())
            accessDao.clearUserBusinessRoles(
                membership.tenantId.value,
                membership.businessId.value,
                membership.id.value,
            )
            membership.roleIds.forEach { roleId ->
                accessDao.addUserBusinessRole(
                    UserBusinessRoleEntity(
                        tenantId = membership.tenantId.value,
                        businessId = membership.businessId.value,
                        userBusinessId = membership.id.value,
                        roleId = roleId.value,
                    )
                )
            }
        }
    }

    override suspend fun getMembership(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): UserBusiness? {
        val entity = accessDao.getMembership(tenantId.value, businessId.value, userId.value)
            ?: return null
        val roles = accessDao.getRoleIds(
            tenantId.value, businessId.value, entity.id
        ).map(::RoleId).toSet()
        return entity.toDomain(roles)
    }

    override suspend fun getPermissionKeys(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): Set<String> = accessDao.getPermissionKeys(
        tenantId.value, businessId.value, userId.value
    ).toSet()

    override suspend fun softDeleteUser(
        userId: UserId,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean = userDao.softDelete(
        userId.value, deletedAt.toEpochMilli(), deletedBy?.value
    ) == 1
}

@Singleton
class RoomSettingsLocalDataSource @Inject constructor(
    private val dao: SettingsDao,
) : SettingsLocalDataSource {
    override fun observeSettings(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessSetting>> =
        dao.observeAll(tenantId.value, businessId.value)
            .map { rows -> rows.map(SettingEntity::toDomain) }

    override suspend fun getSetting(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
    ): BusinessSetting? = dao.get(tenantId.value, businessId.value, key)?.toDomain()

    override suspend fun saveSetting(setting: BusinessSetting) = dao.upsert(setting.toEntity())

    override suspend fun softDeleteSetting(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean = dao.softDelete(
        tenantId.value, businessId.value, key, deletedAt.toEpochMilli(), deletedBy?.value
    ) == 1
}

@Singleton
class RoomConfigurationLocalDataSource @Inject constructor(
    private val dao: FeatureFlagDao,
) : ConfigurationLocalDataSource {
    override fun observeFeatureFlags(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessFeatureFlag>> =
        dao.observeAll(tenantId.value, businessId.value)
            .map { rows -> rows.map(FeatureFlagEntity::toDomain) }

    override suspend fun getFeatureFlag(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
    ): BusinessFeatureFlag? = dao.get(tenantId.value, businessId.value, key)?.toDomain()

    override suspend fun saveFeatureFlag(flag: BusinessFeatureFlag) = dao.upsert(flag.toEntity())
}

@Singleton
class RoomSyncQueueLocalDataSource @Inject constructor(
    private val dao: SyncQueueDao,
) : SyncQueueLocalDataSource {
    override suspend fun enqueue(item: SyncQueueItem) = dao.enqueue(item.toEntity())

    override suspend fun nextBatch(
        tenantId: TenantId,
        businessId: BusinessId,
        statuses: Set<SyncStatus>,
        now: Instant,
        limit: Int,
    ): List<SyncQueueItem> {
        require(limit > 0) { "Sync batch limit must be positive" }
        if (statuses.isEmpty()) return emptyList()
        return dao.nextBatch(
            tenantId.value,
            businessId.value,
            statuses.map { it.name },
            now.toEpochMilli(),
            limit,
        ).map(SyncQueueEntity::toDomain)
    }

    override suspend fun updateState(
        tenantId: TenantId,
        businessId: BusinessId,
        id: SyncOperationId,
        status: SyncStatus,
        attemptCount: Int,
        lastError: String?,
        nextAttemptAt: Instant?,
        updatedAt: Instant,
    ): Boolean {
        require(attemptCount >= 0) { "attemptCount cannot be negative" }
        return dao.updateState(
            tenantId.value,
            businessId.value,
            id.value,
            status.name,
            attemptCount,
            lastError,
            nextAttemptAt?.toEpochMilli(),
            updatedAt.toEpochMilli(),
        ) == 1
    }

    override suspend fun getSummary(
        tenantId: TenantId,
        businessId: BusinessId,
    ): SyncQueueSummary = SyncQueueSummary(
        pending = dao.countByStatus(tenantId.value, businessId.value, SyncStatus.PENDING.name),
        syncing = dao.countByStatus(tenantId.value, businessId.value, SyncStatus.SYNCING.name),
        failed = dao.countByStatus(tenantId.value, businessId.value, SyncStatus.FAILED.name),
        conflicts = dao.countByStatus(tenantId.value, businessId.value, SyncStatus.CONFLICT.name),
    )

    override suspend fun deleteSynced(
        tenantId: TenantId,
        businessId: BusinessId,
        id: SyncOperationId,
    ): Boolean = dao.deleteSynced(tenantId.value, businessId.value, id.value) == 1
}
