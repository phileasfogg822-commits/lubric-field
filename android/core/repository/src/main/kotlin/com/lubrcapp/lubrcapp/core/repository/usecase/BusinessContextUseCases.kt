package com.lubrcapp.lubrcapp.core.repository.usecase

import com.lubrcapp.lubrcapp.core.repository.BusinessChoice
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitcher
import com.lubrcapp.lubrcapp.core.repository.CurrentBusinessProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCurrentBusinessUseCase @Inject constructor(
    private val provider: CurrentBusinessProvider,
) {
    operator fun invoke(userId: UserId): Flow<BusinessChoice?> =
        provider.observeCurrent(userId)
}

class EnsureCurrentBusinessUseCase @Inject constructor(
    private val provider: CurrentBusinessProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        fallback: TenantContext? = null,
    ): BusinessChoice? = provider.ensureCurrent(userId, fallback)
}

class ObserveAvailableBusinessesUseCase @Inject constructor(
    private val switcher: BusinessSwitcher,
) {
    operator fun invoke(userId: UserId): Flow<List<BusinessChoice>> =
        switcher.observeAvailableBusinesses(userId)
}

class SwitchBusinessUseCase @Inject constructor(
    private val switcher: BusinessSwitcher,
) {
    suspend operator fun invoke(
        userId: UserId,
        tenantId: TenantId,
        businessId: BusinessId,
    ): BusinessSwitchResult =
        switcher.switchBusiness(userId, tenantId, businessId)
}
