package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal

/**
 * Phase 14.1 — the typed party (master spec §15/§16) as stored in the
 * "customers" operational-record table.
 *
 * This is the payload-level model: the operational store scopes every
 * record by tenant/business/device externally (OperationalScope), and the
 * record id is the store's id, so neither lives inside this class. The
 * domain module's own Party scaffold is a separate, scope-carrying model;
 * the shared vocabulary between the two is [PartyType], reused here so
 * the whole codebase speaks one type enum.
 *
 * Money fields are plain [BigDecimal] amounts in the business's base
 * currency — §16 defines no per-party currency, and inventing one would
 * fabricate data. [openingBalance] is the only balance fact until the
 * Phase 22 customer ledger exists.
 */
data class Party(
    val id: String,
    val name: String,
    val type: PartyType = PartyType.CUSTOMER,
    val businessName: String? = null,
    val contactPerson: String? = null,
    val mobile: String? = null,
    val whatsapp: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val addresses: List<PartyAddress> = emptyList(),
    val taxNumber: String? = null,
    val nationalId: String? = null,
    val openingBalance: BigDecimal? = null,
    val creditLimit: BigDecimal? = null,
    val creditDays: Int? = null,
    val paymentTerms: String? = null,
    val priceLevel: String? = null,
    val groupId: String? = null,
    val territory: String? = null,
    val salesperson: String? = null,
    val isBlocked: Boolean = false,
    val notes: String? = null,
    /** Photo reference (content or web URI) — display only, never rendered from storage. */
    val photoUri: String? = null,
)

/** One party address (§16: multiple addresses, each billing or shipping). */
data class PartyAddress(
    val kind: Kind,
    val line1: String? = null,
    val line2: String? = null,
    val city: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
) {
    enum class Kind {
        BILLING,
        SHIPPING,
    }
}
