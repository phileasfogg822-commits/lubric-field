package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SyncQueueLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserRemoteDataSource
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.business.Tenant
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.SyncOperationId
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.User
import com.lubrcapp.lubrcapp.domain.user.UserBusiness
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class DefaultBusinessRepository(
    private val local: BusinessLocalDataSource,
    private val remote: BusinessRemoteDataSource,
) : BusinessRepository {
    override fun observeBusinesses(tenantId: TenantId): Flow<List<Business>> =
        local.observeBusinesses(tenantId)

    override suspend fun getBusiness(tenantId: TenantId, businessId: BusinessId): Business? =
        local.getBusiness(tenantId, businessId)

    override suspend fun saveTenant(tenant: Tenant) = local.saveTenant(tenant)

    override suspend fun saveBusiness(business: Business) = local.saveBusiness(business)

    override suspend fun softDeleteBusiness(
        tenantId: TenantId,
        businessId: BusinessId,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean = local.softDeleteBusiness(tenantId, businessId, deletedAt, deletedBy)

    override suspend fun refreshBusinesses(tenantId: TenantId): RefreshResult =
        when (val result = remote.fetchBusinesses(tenantId)) {
            is RemoteResult.Success -> {
                result.value.forEach(local::saveBusiness)
                RefreshResult.Updated(result.value.size)
            }
            is RemoteResult.Unavailable -> RefreshResult.Skipped(result.reason)
            is RemoteResult.Failure -> RefreshResult.Failed(result.message, result.retryable)
        }
}

class DefaultUserRepository(
    private val local: UserLocalDataSource,
    private val remote: UserRemoteDataSource,
) : UserRepository {
    override suspend fun getUser(userId: UserId): User? = local.getUser(userId)
    override suspend fun saveUser(user: User) = local.saveUser(user)
    override suspend fun saveMembership(membership: UserBusiness) = local.saveMembership(membership)
    override suspend fun getMembership(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): UserBusiness? = local.getMembership(tenantId, businessId, userId)

    override suspend fun getPermissionKeys(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): Set<String> = local.getPermissionKeys(tenantId, businessId, userId)

    override suspend fun softDeleteUser(
        userId: UserId,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean = local.softDeleteUser(userId, deletedAt, deletedBy)

    override suspend fun refreshUser(userId: UserId): RefreshResult =
        when (val result = remote.fetchUser(userId)) {
            is RemoteResult.Success -> {
                local.saveUser(result.value)
                RefreshResult.Updated(1)
            }
            is RemoteResult.Unavailable -> RefreshResult.Skipped(result.reason)
            is RemoteResult.Failure -> RefreshResult.Failed(result.message, result.retryable)
        }
}

class DefaultSettingsRepository(
    private val local: SettingsLocalDataSource,
    private val remote: SettingsRemoteDataSource,
) : SettingsRepository {
    override fun observeSettings(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessSetting>> = local.observeSettings(tenantId, businessId)

    override suspend fun getSetting(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
    ): BusinessSetting? = local.getSetting(tenantId, businessId, key)

    override suspend fun saveSetting(setting: BusinessSetting) = local.saveSetting(setting)

    override suspend fun softDeleteSetting(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean = local.softDeleteSetting(tenantId, businessId, key, deletedAt, deletedBy)

    override suspend fun refreshSettings(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RefreshResult = when (val result = remote.fetchSettings(tenantId, businessId)) {
        is RemoteResult.Success -> {
            result.value.forEach(local::saveSetting)
            RefreshResult.Updated(result.value.size)
        }
        is RemoteResult.Unavailable -> RefreshResult.Skipped(result.reason)
        is RemoteResult.Failure -> RefreshResult.Failed(result.message, result.retryable)
    }
}

class DefaultConfigurationRepository(
    private val local: ConfigurationLocalDataSource,
    private val remote: ConfigurationRemoteDataSource,
) : ConfigurationRepository {
    override fun observeFeatureFlags(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessFeatureFlag>> = local.observeFeatureFlags(tenantId, businessId)

    override suspend fun getFeatureFlag(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
    ): BusinessFeatureFlag? = local.getFeatureFlag(tenantId, businessId, key)

    override suspend fun saveFeatureFlag(flag: BusinessFeatureFlag) = local.saveFeatureFlag(flag)

    override suspend fun refreshFeatureFlags(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RefreshResult = when (val result = remote.fetchFeatureFlags(tenantId, businessId)) {
        is RemoteResult.Success -> {
            result.value.forEach(local::saveFeatureFlag)
            RefreshResult.Updated(result.value.size)
        }
        is RemoteResult.Unavailable -> RefreshResult.Skipped(result.reason)
        is RemoteResult.Failure -> RefreshResult.Failed(result.message, result.retryable)
    }
}

class DefaultSyncRepository(
    private val local: SyncQueueLocalDataSource,
) : SyncRepository {
    override suspend fun enqueue(item: SyncQueueItem) = local.enqueue(item)

    override suspend fun nextBatch(
        tenantId: TenantId,
        businessId: BusinessId,
        statuses: Set<SyncStatus>,
        now: Instant,
        limit: Int,
    ): List<SyncQueueItem> = local.nextBatch(tenantId, businessId, statuses, now, limit)

    override suspend fun updateState(
        tenantId: TenantId,
        businessId: BusinessId,
        id: SyncOperationId,
        status: SyncStatus,
        attemptCount: Int,
        lastError: String?,
        nextAttemptAt: Instant?,
        updatedAt: Instant,
    ): Boolean = local.updateState(
        tenantId, businessId, id, status, attemptCount, lastError, nextAttemptAt, updatedAt
    )

    override suspend fun getSummary(
        tenantId: TenantId,
        businessId: BusinessId,
    ): SyncQueueSummary = local.getSummary(tenantId, businessId)

    override suspend fun deleteSynced(
        tenantId: TenantId,
        businessId: BusinessId,
        id: SyncOperationId,
    ): Boolean = local.deleteSynced(tenantId, businessId, id)
}
