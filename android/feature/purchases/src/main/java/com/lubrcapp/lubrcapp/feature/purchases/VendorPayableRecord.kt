package com.lubrcapp.lubrcapp.feature.purchases

import java.math.BigDecimal

/**
 * Phase 23.1 — one payable: the amount owed for a single
 * purchase document, stored as a "vendor_payables" record.
 * That table IS sync-whitelisted but NOT INSERT_ONLY, so
 * payables are mutable by platform rule — a status change
 * (DRAFT to RECEIVED to PAID) overwrites the same record
 * instead of appending a correction, unlike the
 * append-only party_ledger rows of Phase 22.3.
 *
 * One record per purchase: the id IS the purchase
 * document id (the same one-record-per-document shape as
 * SalesDocument in "orders"), so a status transition
 * re-saves the same id and the payable for a purchase is
 * a direct lookup, never a scan-and-match. Status is
 * stored as text (a PurchaseStatus name); the codec
 * never normalizes it.
 *
 * paidAmount is a CACHED value (supervisor decision 1):
 * the derivation from payment records is the authority
 * in later packages, and this field is recomputed on
 * payment/credit events — a stored figure that disagrees
 * with payments is a bug, not a second truth. balanceDue
 * is derived here (total minus paid), mirroring
 * PurchaseInvoice.balanceDue.
 */
data class VendorPayableRecord(
    /** The purchase document id. */
    val id: String,
    val vendorId: String = "",
    val documentNumber: String = "",
    val status: String = "DRAFT",
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val paidAmount: BigDecimal = BigDecimal.ZERO,
    /** CurrencyCode value (e.g. "PKR"). */
    val currency: String = "",
    /** ISO date text as entered (yyyy-MM-dd); no calendar math in this layer. */
    val date: String = "",
    val dueDate: String = "",
    val notes: String = "",
    /** ISO instant the record was first written. */
    val createdAt: String = "",
) {
    val balanceDue: BigDecimal
        get() = totalAmount.subtract(paidAmount)
}
