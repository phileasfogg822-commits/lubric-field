package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money

/**
 * Phase 30.3 — customer portal models (§227 + §226).
 * Pure types: the portal a customer sees, not
 * the portal itself. §227 sections in spec
 * order: Orders, Invoices, Statements, Payments,
 * Catalog. §226 document rows carry Invoice,
 * Amount, Paid and the derived Due; Download
 * and Pay are portal actions, not model fields,
 * so they live in the UI layer, not here.
 * Money rules are reused, never re-derived:
 * amounts are never summed across currencies
 * (the Phase 24 rule), and paid must be in the
 * invoice's currency or the subtraction throws.
 */
enum class PortalSection {
    ORDERS,
    INVOICES,
    STATEMENTS,
    PAYMENTS,
    CATALOG,
}

data class PortalAccount(
    val partyId: String,
    val displayName: String = "",
)

data class PortalOrder(
    val id: String,
    val number: String = "",
    val date: String = "",
    val status: String = "DRAFT",
    val total: Money,
)

data class PortalInvoice(
    val id: String,
    val number: String = "",
    val date: String = "",
    val amount: Money,
    val paid: Money,
)

data class PortalPayment(
    val id: String,
    val date: String = "",
    val amount: Money,
    val reference: String = "",
)

data class PortalCatalogItem(
    val id: String,
    val name: String = "",
    val saleRate: Money? = null,
)

object PortalOps {
    /** Amount still owed; negative when overpaid. */
    fun due(invoice: PortalInvoice): Money = invoice.amount - invoice.paid

    fun canPay(invoice: PortalInvoice): Boolean = due(invoice).isPositive

    /** Total due per currency; currencies are never mixed. */
    fun dueByCurrency(invoices: List<PortalInvoice>): Map<CurrencyCode, Money> {
        val totals = mutableMapOf<CurrencyCode, Money>()
        invoices.forEach { invoice ->
            val owed = due(invoice)
            val currency = owed.currency
            totals[currency] = totals[currency]?.plus(owed) ?: owed
        }
        return totals
    }
}
