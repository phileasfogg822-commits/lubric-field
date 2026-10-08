package com.lubrcapp.lubrcapp.domain.party

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyGroupId
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.TenantId

data class Party(
    val id: PartyId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val type: PartyType = PartyType.CUSTOMER,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val taxNumber: String? = null,
    val groupId: PartyGroupId? = null,
    val openingBalance: Money? = null,
    val creditLimit: Money? = null,
    val isActive: Boolean = true,
    val notes: String? = null,
    val metadata: EntityMetadata,
)

enum class PartyType {
    CUSTOMER,
    SUPPLIER,
    BOTH,
    OTHER,
}
