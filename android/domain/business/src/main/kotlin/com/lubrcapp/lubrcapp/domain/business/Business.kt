package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.BranchId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.ZoneId

data class Tenant(
    val id: TenantId,
    val name: String,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)

data class Business(
    val id: BusinessId,
    val tenantId: TenantId,
    val name: String,
    val businessType: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val country: String = "",
    val postalCode: String = "",
    val website: String = "",
    val taxNumber: String? = null,
    val currency: CurrencyCode,
    val timezone: ZoneId,
    val inviteCode: String? = null,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)

data class Branch(
    val id: BranchId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val name: String,
    val address: String = "",
    val timezone: ZoneId? = null,
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)
