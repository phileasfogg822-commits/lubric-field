package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

data class CreateInitialBusinessRequest(
    val languageCode: String,
    val businessName: String,
    val businessType: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val countryCode: String,
    val postalCode: String = "",
    val website: String = "",
    val currency: CurrencyCode,
    val timezone: ZoneId,
    val taxEnabled: Boolean,
    val taxNumber: String? = null,
    val ownerName: String,
    val ownerPhone: String = "",
    val ownerEmail: String? = null,
) {
    init {
        require(languageCode.isNotBlank()) { "languageCode cannot be blank" }
        require(businessName.isNotBlank()) { "businessName cannot be blank" }
        require(businessType.isNotBlank()) { "businessType cannot be blank" }
        require(countryCode.trim().matches(Regex("[A-Za-z]{2}"))) {
            "countryCode must be an ISO two-letter country code"
        }
        require(ownerName.isNotBlank()) { "ownerName cannot be blank" }
    }
}

data class BusinessBootstrap(
    val tenantId: TenantId,
    val businessId: BusinessId,
    val ownerUserId: UserId,
    val businessName: String,
    val languageCode: String,
)

object BusinessSetupSettingKeys {
    const val OWNER_USER_ID = "business.owner_user_id"
    const val LOCALE_LANGUAGE = "locale.language"
    const val TAX_ENABLED = "tax.enabled"
}

interface DeviceIdentityProvider {
    suspend fun getOrCreate(): DeviceId
}

interface BusinessSetupRepository {
    fun observeBootstrap(): Flow<BusinessBootstrap?>
    suspend fun getBootstrap(): BusinessBootstrap?
    suspend fun createInitialBusiness(
        request: CreateInitialBusinessRequest,
    ): BusinessBootstrap
}
