package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal

/**
 * Phase 18.1 — the typed sales document: a quotation, a sales
 * order, and (from 21.1) an invoice. The header is an "orders"
 * record; each line is an "order_items" record carrying the
 * header's id (both table names are in the Phase 12 sync
 * whitelist, so these documents sync). An invoice is an
 * "orders" record with type INVOICE — same table, so invoices
 * sync without any whitelist edit — reusing the header, lines,
 * numbering, and conversion-link machinery.
 *
 * Amounts are [BigDecimal] end to end and serialize as plain
 * strings, so a stored document round-trips exactly — totals are
 * always recomputed by the Phase 17 engine from these fields,
 * never stored as truth.
 */
enum class SalesDocumentType {
    QUOTATION,
    SALES_ORDER,
    INVOICE,
    SALES_RETURN,
    CREDIT_NOTE,
}

data class SalesDocument(
    val id: String,
    val type: SalesDocumentType,
    /** Assigned by the numbering wiring (18.3); blank while unnumbered. */
    val number: String = "",
    val partyId: String? = null,
    /** ISO date text as entered (yyyy-MM-dd); no calendar math in this layer. */
    val date: String = "",
    val dueDate: String? = null,
    /** Stored as text in 18.1; the typed status engine replaces it in 18.2. */
    val status: String = "DRAFT",
    val notes: String = "",
    /** Conversion links (§43), written by 18.5; null until converted. */
    val convertedFromId: String? = null,
    val convertedToId: String? = null,
)

/** One tax component as it stood when the line was entered. */
data class SalesTaxComponent(
    val name: String,
    val ratePercent: BigDecimal,
)

data class SalesDocumentLine(
    val id: String,
    val orderId: String,
    /** Display order within the document. */
    val position: Int = 0,
    val itemId: String? = null,
    val description: String = "",
    val quantity: BigDecimal,
    val unitId: String? = null,
    val rate: BigDecimal,
    /** "PERCENT" or "FIXED" (§53), matching the engine's Discount kinds. */
    val discountKind: String? = null,
    val discountValue: BigDecimal? = null,
    /**
     * The tax snapshot: the scheme's components copied onto the line
     * at entry time, so editing a scheme later can never change an
     * existing document. Empty = untaxed line.
     */
    val taxInclusive: Boolean = false,
    val taxComponents: List<SalesTaxComponent> = emptyList(),
    val warehouseId: String? = null,
    val batchId: String? = null,
    /** §44 fulfilment state; the rules over it land in 18.2. */
    val fulfilledQuantity: BigDecimal = BigDecimal.ZERO,
    /** Serial numbers sold on this line (§30); picked at sale time, flipped SOLD by 21.3. */
    val serialNumbers: List<String> = emptyList(),
)
