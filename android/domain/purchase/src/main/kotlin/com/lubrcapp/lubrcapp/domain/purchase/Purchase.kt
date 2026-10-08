package com.lubrcapp.lubrcapp.domain.purchase

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.PurchaseId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.TransactionLineId
import java.time.LocalDate

data class PurchaseInvoice(
    val id: PurchaseId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val partyId: PartyId,
    val invoiceNumber: String,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val status: PurchaseStatus = PurchaseStatus.DRAFT,
    val subtotal: Money,
    val taxTotal: Money,
    val discount: Money,
    val total: Money,
    val paidAmount: Money,
    val notes: String? = null,
    val metadata: EntityMetadata,
) {
    val balanceDue: Money
        get() = total - paidAmount
}

data class PurchaseInvoiceLine(
    val id: TransactionLineId,
    val purchaseId: PurchaseId,
    val itemId: ItemId,
    val description: String = "",
    val quantity: Quantity,
    val unitPrice: Money,
    val taxRate: Percentage = Percentage.ZERO,
    val discount: Money,
    val lineTotal: Money,
)

enum class PurchaseStatus {
    DRAFT,
    RECEIVED,
    PAID,
    PARTIALLY_PAID,
    OVERDUE,
    CANCELLED,
}
