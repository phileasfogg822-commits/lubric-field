package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * Phase 16.3 — editable form state for the price-list form (§26).
 * Name only; the rates live on the entries.
 */
class PriceListFormState {
    var name by mutableStateOf("")

    /** Starts a blank form (new price list). */
    fun reset() {
        load(PriceList(id = "", name = ""))
    }

    /** Loads an existing price list into the form for editing. */
    fun load(priceList: PriceList) {
        name = priceList.name
    }

    /** The price list to save under [id]. */
    fun toPriceList(id: String): PriceList = PriceList(
        id = id,
        name = name.trim(),
    )
}

/**
 * Phase 16.3 — editable form state for a price-list entry (§26).
 *
 * [toEntry] returns null when no item is picked or the rate text
 * is blank, non-numeric, or negative — a list rate of zero is
 * legitimate (a promotional free rate) and saves. The list the
 * entry belongs to is supplied by the mode, not edited here.
 */
class PriceListItemFormState {
    var itemId by mutableStateOf<String?>(null)
    var rate by mutableStateOf("")

    /** Starts a blank form (new entry). */
    fun reset() {
        itemId = null
        rate = ""
    }

    /** Loads an existing entry into the form for editing. */
    fun load(entry: PriceListItem) {
        itemId = entry.itemId.takeIf { it.isNotEmpty() }
        rate = entry.rate?.toPlainString().orEmpty()
    }

    /** The entry to save under [id] in [priceListId], or null when invalid. */
    fun toEntry(id: String, priceListId: String): PriceListItem? {
        val item = itemId ?: return null
        val parsed = rate.trim().toBigDecimalOrNull() ?: return null
        if (parsed.signum() < 0) return null
        return PriceListItem(
            id = id,
            priceListId = priceListId,
            itemId = item,
            rate = parsed,
        )
    }
}
