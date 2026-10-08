package com.lubrcapp.lubrcapp.core.repository.usecase

import com.lubrcapp.lubrcapp.core.repository.BusinessFeatureFlag
import com.lubrcapp.lubrcapp.core.repository.BusinessRepository
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.core.repository.ConfigurationRepository
import com.lubrcapp.lubrcapp.core.repository.SettingsRepository
import com.lubrcapp.lubrcapp.core.repository.SyncQueueItem
import com.lubrcapp.lubrcapp.core.repository.SyncQueueSummary
import com.lubrcapp.lubrcapp.core.repository.SyncRepository
import com.lubrcapp.lubrcapp.core.repository.UserRepository
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import kotlinx.coroutines.flow.Flow

class ObserveBusinessesUseCase(
    private val repository: BusinessRepository,
) {
    operator fun invoke(tenantId: TenantId): Flow<List<Business>> =
        repository.observeBusinesses(tenantId)
}

class GetBusinessUseCase(
    private val repository: BusinessRepository,
) {
    suspend operator fun invoke(tenantId: TenantId, businessId: BusinessId): Business? =
        repository.getBusiness(tenantId, businessId)
}

class SaveBusinessUseCase(
    private val repository: BusinessRepository,
) {
    suspend operator fun invoke(business: Business) = repository.saveBusiness(business)
}

class GetUserPermissionsUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
        userId: UserId,
    ): Set<String> = repository.getPermissionKeys(tenantId, businessId, userId)
}

class ObserveSettingsUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessSetting>> = repository.observeSettings(tenantId, businessId)
}

class SaveSettingUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(setting: BusinessSetting) = repository.saveSetting(setting)
}

class ObserveFeatureFlagsUseCase(
    private val repository: ConfigurationRepository,
) {
    operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<List<BusinessFeatureFlag>> = repository.observeFeatureFlags(tenantId, businessId)
}

class EnqueueSyncOperationUseCase(
    private val repository: SyncRepository,
) {
    suspend operator fun invoke(item: SyncQueueItem) = repository.enqueue(item)
}

class GetSyncQueueSummaryUseCase(
    private val repository: SyncRepository,
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        businessId: BusinessId,
    ): SyncQueueSummary = repository.getSummary(tenantId, businessId)
}
