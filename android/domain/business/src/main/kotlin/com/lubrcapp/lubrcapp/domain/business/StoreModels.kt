package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Quantity

/**
 * Phase 30.4 — online store models (spec
 * Phase 10 "Online Store"). Pure types: the
 * storefront is the published catalog, and
 * intake validation checks a store order
 * against it before anything is posted. No
 * checkout, no payment, no fulfillment here —
 * intake only. Totals never mix currencies
 * (the Phase 24 rule): a mixed-currency
 * order throws rather than summing.
 */
data class StoreItem(
    val id: String,
    val name: String = "",
    val saleRate: Money? = null,
    val isPublished: Boolean = true,
)

data class StoreOrderLine(
    val itemId: String,
    val quantity: Quantity,
    val rate: Money,
)

data class StoreOrder(
    val id: String,
    val partyId: String,
    val lines: List<StoreOrderLine> = emptyList(),
)

sealed interface StoreOrderValidation {
    data object Valid : StoreOrderValidation
    data class Invalid(val reasons: List<String>) : StoreOrderValidation
}

object StorefrontOps {
    fun visible(items: List<StoreItem>): List<StoreItem> =
        items.filter { it.isPublished }

    fun validate(order: StoreOrder, catalog: List<StoreItem>): StoreOrderValidation {
        val byId = catalog.associateBy { it.id }
        val reasons = mutableListOf<String>()
        order.lines.forEach { line ->
            val item = byId[line.itemId]
            when {
                item == null -> reasons += "Unknown item ${line.itemId}"
                !item.isPublished -> reasons += "Unpublished item ${line.itemId}"
                else -> {
                    if (!line.quantity.isPositive) {
                        reasons += "Non-positive quantity for ${line.itemId}"
                    }
                    if (item.saleRate != null && item.saleRate != line.rate) {
                        reasons += "Rate mismatch for ${line.itemId}"
                    }
                }
            }
        }
        return if (reasons.isEmpty()) StoreOrderValidation.Valid else StoreOrderValidation.Invalid(reasons)
    }

    fun total(order: StoreOrder): Money {
        require(order.lines.isNotEmpty()) { "Order ${order.id} has no lines" }
        val currency: CurrencyCode = order.lines.first().rate.currency
        var sum = Money.zero(currency)
        order.lines.forEach { line ->
            require(line.rate.currency == currency) { "Currency mismatch in order ${order.id}" }
            sum += Money.of(line.rate.amount.multiply(line.quantity.amount), currency)
        }
        return sum
    }
}
