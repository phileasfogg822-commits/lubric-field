package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * Phase 14.3 — editable form state for the customer-group form (§19).
 * Same contract as [PartyFormState]: text-first fields, blank
 * optionals become null, and a non-blank discount that is not a
 * number makes [toGroup] return null so the screen can show the
 * form error instead of saving corrupt data.
 */
class PartyGroupFormState {
    var name by mutableStateOf("")
    var priceLevel by mutableStateOf("")
    var discountPercent by mutableStateOf("")
    var paymentTerms by mutableStateOf("")
    var taxRule by mutableStateOf("")
    var salesperson by mutableStateOf("")
    var territory by mutableStateOf("")

    /** Starts a blank form (new group). */
    fun reset() {
        load(PartyGroup(id = "", name = ""))
    }

    /** Loads an existing group into the form for editing. */
    fun load(group: PartyGroup) {
        name = group.name
        priceLevel = group.priceLevel.orEmpty()
        discountPercent = group.discountPercent?.toPlainString().orEmpty()
        paymentTerms = group.paymentTerms.orEmpty()
        taxRule = group.taxRule.orEmpty()
        salesperson = group.salesperson.orEmpty()
        territory = group.territory.orEmpty()
    }

    /** The group to save under [id], or null when the discount is not a number. */
    fun toGroup(id: String): PartyGroup? {
        val discountText = discountPercent.trim()
        val discount: BigDecimal? = if (discountText.isEmpty()) {
            null
        } else {
            discountText.toBigDecimalOrNull() ?: return null
        }
        return PartyGroup(
            id = id,
            name = name.trim(),
            priceLevel = priceLevel.trimmedOrNull(),
            discountPercent = discount,
            paymentTerms = paymentTerms.trimmedOrNull(),
            taxRule = taxRule.trimmedOrNull(),
            salesperson = salesperson.trimmedOrNull(),
            territory = territory.trimmedOrNull(),
        )
    }
}

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
