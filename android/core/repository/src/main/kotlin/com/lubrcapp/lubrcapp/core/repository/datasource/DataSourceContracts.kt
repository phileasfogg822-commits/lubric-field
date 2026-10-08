package com.lubrcapp.lubrcapp.core.repository.datasource

import com.lubrcapp.lubrcapp.core.repository.BusinessFeatureFlag
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.core.repository.RemoteResult
import com.lubrcapp.lubrcapp.core.repository.SyncQueueItem
import com.lubrcapp.lubrcapp.core.repository.SyncQueueSummary
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

interface BusinessLocalDataSource {
    fun observeBusinesses(tenantId: TenantId): Flow<List<Business>>
    suspend fun getBusiness(tenantId: TenantId, businessId: BusinessId): Business?
    suspend fun saveTenant(tenant: Tenant)
    suspend fun saveBusiness(business: Business)
    suspend fun softDeleteBusiness(
        tenantId: TenantId,
        businessId: BusinessId,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean
}

interface BusinessRemoteDataSource {
    suspend fun fetchBusinesses(tenantId: TenantId): RemoteResult<List<Business>>
}

interface UserLocalDataSource {
    suspend fun getUser(userId: UserId): User?
    suspend fun saveUser(user: User)
    suspend fun saveMembership(membership: UserBusiness)
    suspend fun getMembership(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): UserBusiness?
    suspend fun getPermissionKeys(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): Set<String>
    suspend fun softDeleteUser(
        userId: UserId,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean
}

interface UserRemoteDataSource {
    suspend fun fetchUser(userId: UserId): RemoteResult<User>
}

interface SettingsLocalDataSource {
    fun observeSettings(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessSetting>>
    suspend fun getSetting(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
    ): BusinessSetting?
    suspend fun saveSetting(setting: BusinessSetting)
    suspend fun softDeleteSetting(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
        deletedAt: Instant,
        deletedBy: UserId?,
    ): Boolean
}

interface SettingsRemoteDataSource {
    suspend fun fetchSettings(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RemoteResult<List<BusinessSetting>>
}

interface ConfigurationLocalDataSource {
    fun observeFeatureFlags(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessFeatureFlag>>
    suspend fun getFeatureFlag(
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
    ): BusinessFeatureFlag?
    suspend fun saveFeatureFlag(flag: BusinessFeatureFlag)
}

interface ConfigurationRemoteDataSource {
    suspend fun fetchFeatureFlags(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RemoteResult<List<BusinessFeatureFlag>>
}

interface SyncQueueLocalDataSource {
    suspend fun enqueue(item: SyncQueueItem)
    suspend fun nextBatch(
        tenantId: TenantId,
        businessId: BusinessId,
        statuses: Set<SyncStatus>,
        now: Instant,
        limit: Int,
    ): List<SyncQueueItem>
    suspend fun updateState(
        tenantId: TenantId,
        businessId: BusinessId,
        id: SyncOperationId,
        status: SyncStatus,
        attemptCount: Int,
        lastError: String?,
        nextAttemptAt: Instant?,
        updatedAt: Instant,
    ): Boolean
    suspend fun getSummary(
        tenantId: TenantId,
        businessId: BusinessId,
    ): SyncQueueSummary
    suspend fun deleteSynced(
        tenantId: TenantId,
        businessId: BusinessId,
        id: SyncOperationId,
    ): Boolean
}
