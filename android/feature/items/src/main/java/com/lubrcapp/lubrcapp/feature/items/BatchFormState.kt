package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * Phase 16.4 — editable form state for the batch form (§29).
 *
 * Text-first like the other catalog forms: dates stay text (ISO
 * yyyy-MM-dd by convention — stored as entered, never parsed),
 * blank optionals become null, and a non-blank numeric field that
 * does not parse makes [toBatch] return null so the screen shows
 * the form error instead of saving corrupt data. The item the
 * batch belongs to comes from the picker.
 */
class BatchFormState {
    var itemId by mutableStateOf<String?>(null)
    var batchNumber by mutableStateOf("")
    var manufacturingDate by mutableStateOf("")
    var expiryDate by mutableStateOf("")
    var purchaseRate by mutableStateOf("")
    var saleRate by mutableStateOf("")
    var mrp by mutableStateOf("")
    var quantity by mutableStateOf("")

    /** Starts a blank form (new batch). */
    fun reset() {
        load(Batch(id = "", itemId = "", batchNumber = ""))
    }

    /** Loads an existing batch into the form for editing. */
    fun load(batch: Batch) {
        itemId = batch.itemId.takeIf { it.isNotEmpty() }
        batchNumber = batch.batchNumber
        manufacturingDate = batch.manufacturingDate.orEmpty()
        expiryDate = batch.expiryDate.orEmpty()
        purchaseRate = batch.purchaseRate?.toPlainString().orEmpty()
        saleRate = batch.saleRate?.toPlainString().orEmpty()
        mrp = batch.mrp?.toPlainString().orEmpty()
        quantity = batch.quantity?.toPlainString().orEmpty()
    }

    /** The batch to save under [id], or null when invalid. */
    fun toBatch(id: String): Batch? {
        val item = itemId ?: return null
        if (batchNumber.isBlank()) return null
        val purchase = parseAmount(purchaseRate) ?: return null
        val sale = parseAmount(saleRate) ?: return null
        val mrpValue = parseAmount(mrp) ?: return null
        val qty = parseAmount(quantity) ?: return null
        return Batch(
            id = id,
            itemId = item,
            batchNumber = batchNumber.trim(),
            manufacturingDate = manufacturingDate.trimmedOrNull(),
            expiryDate = expiryDate.trimmedOrNull(),
            purchaseRate = purchase.value,
            saleRate = sale.value,
            mrp = mrpValue.value,
            quantity = qty.value,
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
