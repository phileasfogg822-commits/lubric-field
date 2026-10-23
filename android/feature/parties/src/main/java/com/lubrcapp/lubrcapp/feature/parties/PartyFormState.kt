package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal

/**
 * Phase 14.2 — editable form state for the party form (spec §16).
 *
 * Text fields stay text until [toParty] so the user can type partial
 * numbers; blank optional fields become null, and a non-blank field
 * that does not parse as a number makes [toParty] return null so the
 * screen can show the form error instead of saving corrupt data.
 *
 * [groupId] is carried through edits but has no editor yet — customer
 * groups arrive in Package 14.3; dropping the link on every edit would
 * silently destroy data.
 */
class PartyFormState {
    var name by mutableStateOf("")
    var type by mutableStateOf(PartyType.CUSTOMER)
    var businessName by mutableStateOf("")
    var contactPerson by mutableStateOf("")
    var mobile by mutableStateOf("")
    var whatsapp by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var addresses by mutableStateOf<List<PartyAddressFormState>>(emptyList())
    var taxNumber by mutableStateOf("")
    var nationalId by mutableStateOf("")
    var openingBalance by mutableStateOf("")
    var creditLimit by mutableStateOf("")
    var creditDays by mutableStateOf("")
    var paymentTerms by mutableStateOf("")
    var priceLevel by mutableStateOf("")
    var groupId: String? = null
    var territory by mutableStateOf("")
    var salesperson by mutableStateOf("")
    var isBlocked by mutableStateOf(false)
    var notes by mutableStateOf("")

    /** Starts a blank form (new party). */
    fun reset() {
        load(Party(id = "", name = ""))
    }

    /** Loads an existing party into the form for editing. */
    fun load(party: Party) {
        name = party.name
        type = party.type
        businessName = party.businessName.orEmpty()
        contactPerson = party.contactPerson.orEmpty()
        mobile = party.mobile.orEmpty()
        whatsapp = party.whatsapp.orEmpty()
        phone = party.phone.orEmpty()
        email = party.email.orEmpty()
        addresses = party.addresses.map { PartyAddressFormState.from(it) }
        taxNumber = party.taxNumber.orEmpty()
        nationalId = party.nationalId.orEmpty()
        openingBalance = party.openingBalance?.toPlainString().orEmpty()
        creditLimit = party.creditLimit?.toPlainString().orEmpty()
        creditDays = party.creditDays?.toString().orEmpty()
        paymentTerms = party.paymentTerms.orEmpty()
        priceLevel = party.priceLevel.orEmpty()
        groupId = party.groupId
        territory = party.territory.orEmpty()
        salesperson = party.salesperson.orEmpty()
        isBlocked = party.isBlocked
        notes = party.notes.orEmpty()
    }

    /**
     * Builds the party to save under [id], or null when a numeric field
     * holds non-blank text that is not a number.
     */
    fun toParty(id: String): Party? {
        val opening = parseAmount(openingBalance) ?: return null
        val limit = parseAmount(creditLimit) ?: return null
        val days = parseDays(creditDays) ?: return null
        return Party(
            id = id,
            name = name.trim(),
            type = type,
            businessName = businessName.trimmedOrNull(),
            contactPerson = contactPerson.trimmedOrNull(),
            mobile = mobile.trimmedOrNull(),
            whatsapp = whatsapp.trimmedOrNull(),
            phone = phone.trimmedOrNull(),
            email = email.trimmedOrNull(),
            addresses = addresses.mapNotNull { it.toAddressOrNull() },
            taxNumber = taxNumber.trimmedOrNull(),
            nationalId = nationalId.trimmedOrNull(),
            openingBalance = opening.value,
            creditLimit = limit.value,
            creditDays = days.value,
            paymentTerms = paymentTerms.trimmedOrNull(),
            priceLevel = priceLevel.trimmedOrNull(),
            groupId = groupId,
            territory = territory.trimmedOrNull(),
            salesperson = salesperson.trimmedOrNull(),
            isBlocked = isBlocked,
            notes = notes.trimmedOrNull(),
        )
    }

    private class Parsed<T>(val value: T?)

    private fun parseAmount(text: String): Parsed<BigDecimal>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Parsed(null)
        return trimmed.toBigDecimalOrNull()?.let { Parsed(it) }
    }

    private fun parseDays(text: String): Parsed<Int>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Parsed(null)
        return trimmed.toIntOrNull()?.let { Parsed(it) }
    }
}

/** Editable state for one address row in the party form. */
class PartyAddressFormState {
    var kind by mutableStateOf(PartyAddress.Kind.BILLING)
    var line1 by mutableStateOf("")
    var line2 by mutableStateOf("")
    var city by mutableStateOf("")
    var region by mutableStateOf("")
    var postalCode by mutableStateOf("")
    var country by mutableStateOf("")

    /** The address to save, or null when every field is blank (row dropped). */
    fun toAddressOrNull(): PartyAddress? {
        val fields = listOf(line1, line2, city, region, postalCode, country)
        if (fields.all { it.isBlank() }) return null
        return PartyAddress(
            kind = kind,
            line1 = line1.trimmedOrNull(),
            line2 = line2.trimmedOrNull(),
            city = city.trimmedOrNull(),
            region = region.trimmedOrNull(),
            postalCode = postalCode.trimmedOrNull(),
            country = country.trimmedOrNull(),
        )
    }

    companion object {
        fun from(address: PartyAddress): PartyAddressFormState = PartyAddressFormState().apply {
            kind = address.kind
            line1 = address.line1.orEmpty()
            line2 = address.line2.orEmpty()
            city = address.city.orEmpty()
            region = address.region.orEmpty()
            postalCode = address.postalCode.orEmpty()
            country = address.country.orEmpty()
        }
    }
}

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
