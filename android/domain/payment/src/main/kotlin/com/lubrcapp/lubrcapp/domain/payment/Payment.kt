package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.PaymentId
import com.lubrcapp.lubrcapp.domain.common.PaymentMethodId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.LocalDate

data class Payment(
    val id: PaymentId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val partyId: PartyId,
    val direction: PaymentDirection,
    val methodId: PaymentMethodId,
    val amount: Money,
    val referenceNumber: String? = null,
    val relatedInvoiceId: InvoiceId? = null,
    val date: LocalDate,
    val notes: String? = null,
    val metadata: EntityMetadata,
)

enum class PaymentDirection {
    RECEIVED,
    PAID,
}
