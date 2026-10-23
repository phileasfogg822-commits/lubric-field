package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal

/**
 * Phase 16.3 — the pure price-resolution rule (master spec §26).
 *
 * For an item under a chosen price list: the list's own rate for
 * that item wins; without a usable entry, the item's own sale rate
 * applies; with neither, the result is null — no rate is ever
 * invented. Entries from other lists never leak in, and an entry
 * whose stored rate is missing/unreadable (null) is ignored.
 *
 * Nothing consumes this rule yet: documents and invoices arrive
 * in Phases 18/21, and they will resolve through here rather than
 * re-deriving precedence.
 */
object PriceListResolution {

    fun resolve(item: Item, priceListId: String, entries: List<PriceListItem>): BigDecimal? {
        val entry = entries.firstOrNull {
            it.priceListId == priceListId && it.itemId == item.id && it.rate != null
        }
        return entry?.rate ?: item.saleRate
    }
}
