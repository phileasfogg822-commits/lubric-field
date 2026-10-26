package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId

/**
 * Phase 27.3 — field collection (§111 Collection Mode).
 * Pure model: a salesperson collects a payment from a
 * customer in the field. The §111 fields are Customer,
 * Outstanding, Collection Amount, Payment Mode,
 * Reference, Receipt — Outstanding is DERIVED (the
 * 22.x rule: paidAmount is derived, never stored), so
 * this record carries the collected amount, not the
 * outstanding; the screen derives outstanding from the
 * party ledger / open invoices at display time.
 *
 * Posting is NOT here: how a collection becomes a
 * payment row, a cash movement (INSERT_ONLY, 22.x) and
 * a journal entry is a later package / shell decision.
 * What 27.3 pins is the record plus its allocation:
 * [FieldCollectionOps.allocate] runs the 22.1
 * allocation core (oldest first, same party, clamped
 * at outstanding, leftover unallocated) without
 * duplicating its rules.
 *
 * The record is deliberately NOT named `Collection`:
 * that would shadow Kotlin's stdlib type in this
 * package.
 */
enum class CollectionMode {
    CASH,
    CARD,
    BANK_TRANSFER,
    CHEQUE,
    OTHER,
}

data class FieldCollection(
    val id: String,
    val partyId: PartyId,
    val amount: Money,
    val mode: CollectionMode = CollectionMode.CASH,
    val reference: String = "",
    val receiptNumber: String = "",
    val date: String = "",
)

object FieldCollectionOps {
    fun validate(collection: FieldCollection) {
        require(collection.amount.isPositive) {
            "Collection amount must be positive: ${collection.id}"
        }
    }

    fun allocate(
        collection: FieldCollection,
        openInvoices: List<OpenInvoice>,
    ): AllocationResult =
        PaymentAllocation.allocate(collection.partyId, collection.amount, openInvoices)
}
