package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal

/**
 * Phase 16.3 — one item's rate inside one price list (master spec
 * §26), stored as its own operational record type
 * ("price_list_items" — the table name the Phase 12 sync store
 * already whitelists).
 *
 * [rate] is nullable because a stored record can be missing or
 * carry an unreadable rate; such an entry is data damage, not a
 * price, and [PriceListResolution] ignores it (falling back to the
 * item's own sale rate) rather than inventing a number. The form
 * only ever saves a parsed rate of zero or more.
 */
data class PriceListItem(
    val id: String,
    val priceListId: String,
    val itemId: String,
    val rate: BigDecimal?,
)
