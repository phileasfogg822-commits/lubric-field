package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

data class TenantContext(
    val tenantId: TenantId,
    val businessId: BusinessId,
    val userId: UserId,
)

data class BusinessChoice(
    val context: TenantContext,
    val name: String,
    val businessType: String,
    val currency: CurrencyCode,
    val timezone: ZoneId,
    val languageCode: String,
)

sealed interface BusinessSwitchResult {
    data class Switched(val business: BusinessChoice) : BusinessSwitchResult
    data class AccessDenied(
        val tenantId: TenantId,
        val businessId: BusinessId,
    ) : BusinessSwitchResult
    data class Unavailable(
        val tenantId: TenantId,
        val businessId: BusinessId,
    ) : BusinessSwitchResult
}

interface CurrentBusinessProvider {
    fun observeCurrent(userId: UserId): Flow<BusinessChoice?>

    suspend fun current(userId: UserId): BusinessChoice?

    /**
     * Restores a persisted selection only after membership/business validation.
     * If it is missing or invalid, [fallback] is validated next, then the first
     * accessible business is selected.
     */
    suspend fun ensureCurrent(
        userId: UserId,
        fallback: TenantContext? = null,
    ): BusinessChoice?
}

interface BusinessSwitcher {
    fun observeAvailableBusinesses(userId: UserId): Flow<List<BusinessChoice>>

    suspend fun switchBusiness(
        userId: UserId,
        tenantId: TenantId,
        businessId: BusinessId,
    ): BusinessSwitchResult
}
