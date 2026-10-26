package com.lubrcapp.lubrcapp.feature.printing

import java.math.BigDecimal

/**
 * Phase 25.1 — the printable document: the single shape
 * every render path consumes (PDF, print preview,
 * thermal receipt). Plain data, no feature or domain
 * dependency — the app shell maps sales, purchase, and
 * report data into it, because feature modules never
 * depend on each other. Amounts are BigDecimals.
 * Currency is a plain code string ("" when the source
 * carries none, e.g. journal lines).
 */
data class PrintableLine(
    val description: String,
    val quantity: BigDecimal,
    val rate: BigDecimal,
    val amount: BigDecimal,
)

data class PrintableDocument(
    val title: String,
    val documentNumber: String,
    val date: String,
    val partyName: String?,
    val lines: List<PrintableLine>,
    val subtotal: BigDecimal,
    val taxTotal: BigDecimal,
    val discountTotal: BigDecimal,
    val grandTotal: BigDecimal,
    val currency: String,
    val notes: String?,
    val footer: String?,
)

/**
 * Builds a printable document. The subtotal is the sum
 * of line amounts AS GIVEN — source engines own
 * per-line rounding, the builder sums, it does not
 * re-derive. Grand total is subtotal + tax − discount.
 * Tax and discount are non-negative magnitudes; a
 * negative value is a caller error.
 */
fun printableDocument(
    title: String,
    documentNumber: String,
    date: String,
    partyName: String? = null,
    lines: List<PrintableLine>,
    taxTotal: BigDecimal = BigDecimal.ZERO,
    discountTotal: BigDecimal = BigDecimal.ZERO,
    currency: String = "",
    notes: String? = null,
    footer: String? = null,
): PrintableDocument {
    require(taxTotal >= BigDecimal.ZERO) { "taxTotal must not be negative" }
    require(discountTotal >= BigDecimal.ZERO) { "discountTotal must not be negative" }
    val subtotal = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.amount }
    return PrintableDocument(
        title = title,
        documentNumber = documentNumber,
        date = date,
        partyName = partyName,
        lines = lines,
        subtotal = subtotal,
        taxTotal = taxTotal,
        discountTotal = discountTotal,
        grandTotal = subtotal + taxTotal - discountTotal,
        currency = currency,
        notes = notes,
        footer = footer,
    )
}
