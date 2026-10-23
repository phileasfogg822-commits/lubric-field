package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * Phase 15.2 — editable form state for the item form (spec §20/§26).
 *
 * Text fields stay text until [toItem] so the user can type partial
 * numbers; blank optional fields become null, and a non-blank field
 * that does not parse as a number makes [toItem] return null so the
 * screen can show the form error instead of saving corrupt data.
 *
 * [categoryId] / [brandId] are the §23/§24 links: Package 15.3 adds
 * their entities and pickers; until then the values are carried
 * through edits untouched, exactly as the party form carried its
 * group link before the group picker existed.
 */
class ItemFormState {
    var name by mutableStateOf("")
    var type by mutableStateOf(ItemType.PRODUCT)
    var sku by mutableStateOf("")
    var barcode by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var brandId by mutableStateOf<String?>(null)
    var unit by mutableStateOf("")
    var purchaseRate by mutableStateOf("")
    var saleRate by mutableStateOf("")
    var wholesaleRate by mutableStateOf("")
    var dealerRate by mutableStateOf("")
    var minimumRate by mutableStateOf("")
    var mrp by mutableStateOf("")
    var taxPercent by mutableStateOf("")
    var openingStock by mutableStateOf("")
    var description by mutableStateOf("")

    /** Starts a blank form (new item). */
    fun reset() {
        load(Item(id = "", name = ""))
    }

    /** Loads an existing item into the form for editing. */
    fun load(item: Item) {
        name = item.name
        type = item.type
        sku = item.sku.orEmpty()
        barcode = item.barcode.orEmpty()
        categoryId = item.categoryId
        brandId = item.brandId
        unit = item.unit.orEmpty()
        purchaseRate = item.purchaseRate?.toPlainString().orEmpty()
        saleRate = item.saleRate?.toPlainString().orEmpty()
        wholesaleRate = item.wholesaleRate?.toPlainString().orEmpty()
        dealerRate = item.dealerRate?.toPlainString().orEmpty()
        minimumRate = item.minimumRate?.toPlainString().orEmpty()
        mrp = item.mrp?.toPlainString().orEmpty()
        taxPercent = item.taxPercent?.toPlainString().orEmpty()
        openingStock = item.openingStock?.toPlainString().orEmpty()
        description = item.description.orEmpty()
    }

    /**
     * Builds the item to save under [id], or null when a numeric field
     * holds non-blank text that is not a number.
     */
    fun toItem(id: String): Item? {
        val purchase = parseAmount(purchaseRate) ?: return null
        val sale = parseAmount(saleRate) ?: return null
        val wholesale = parseAmount(wholesaleRate) ?: return null
        val dealer = parseAmount(dealerRate) ?: return null
        val minimum = parseAmount(minimumRate) ?: return null
        val mrpValue = parseAmount(mrp) ?: return null
        val tax = parseAmount(taxPercent) ?: return null
        val stock = parseAmount(openingStock) ?: return null
        return Item(
            id = id,
            name = name.trim(),
            type = type,
            sku = sku.trimmedOrNull(),
            barcode = barcode.trimmedOrNull(),
            categoryId = categoryId,
            brandId = brandId,
            unit = unit.trimmedOrNull(),
            purchaseRate = purchase.value,
            saleRate = sale.value,
            wholesaleRate = wholesale.value,
            dealerRate = dealer.value,
            minimumRate = minimum.value,
            mrp = mrpValue.value,
            taxPercent = tax.value,
            openingStock = stock.value,
            description = description.trimmedOrNull(),
        )
    }

    private class Parsed<T>(val value: T?)

    private fun parseAmount(text: String): Parsed<BigDecimal>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Parsed(null)
        return trimmed.toBigDecimalOrNull()?.let { Parsed(it) }
    }
}

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
