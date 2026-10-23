package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal

/**
 * Phase 16.4 — a stock batch for one item (master spec §29),
 * stored as its own operational record type ("batches") in the
 * same scoped store as items. Batch tracking is optional per
 * item: an item simply has no batch records until someone adds
 * one.
 *
 * Dates are ISO date strings (yyyy-MM-dd) exactly as entered —
 * this package stores them as data and does no calendar math.
 * [quantity] and the rates are stored data only: the stock
 * ledger is Phase 19, and a batch quantity recorded here never
 * moves, sums, or drives availability in this phase. All numeric
 * fields are nullable because a stored record can be missing
 * them; the form only saves parsed numbers.
 */
data class Batch(
    val id: String,
    val itemId: String,
    val batchNumber: String,
    val manufacturingDate: String? = null,
    val expiryDate: String? = null,
    val purchaseRate: BigDecimal? = null,
    val saleRate: BigDecimal? = null,
    val mrp: BigDecimal? = null,
    val quantity: BigDecimal? = null,
)
