package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.printing.PrintableDocument
import com.lubrcapp.lubrcapp.feature.printing.PrintableLine
import com.lubrcapp.lubrcapp.feature.printing.printableDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentEngine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine

/**
 * Phase 25.7 — the shell-side map from a stored sales invoice to the
 * printable document the preview screen renders. The totals engine
 * owns every amount (never re-derived): per-line amounts come from
 * the engine's per-line TAXABLE BASE, not its line total — the
 * builder's grand total is subtotal + tax − discount, so feeding it
 * tax-inclusive line totals would double-count the tax. Lines sort
 * by position (the engine sorts the same way, so the zip aligns).
 * Blank notes map to null, so the layout emits no dangling
 * "Notes: " row. Currency defaults to XXX — the sales screen's own
 * default — because documents carry no currency and the app-wide
 * currency wiring is still the open carryover; the amounts'
 * arithmetic does not depend on the code.
 */
fun latestInvoice(invoices: List<SalesDocument>): SalesDocument? =
    invoices.maxWithOrNull(compareBy({ it.date }, { it.id }))

fun invoiceToPrintable(
    invoice: SalesDocument,
    lines: List<SalesDocumentLine>,
    partyName: String?,
    currency: CurrencyCode = CurrencyCode("XXX"),
): PrintableDocument {
    val sorted = lines.sortedBy { it.position }
    val totals = SalesDocumentEngine.totals(sorted, currency)
    return printableDocument(
        title = "Invoice",
        documentNumber = invoice.number,
        date = invoice.date,
        partyName = partyName,
        lines = sorted.zip(totals.lines) { line, lineTotals ->
            PrintableLine(line.description, line.quantity, line.rate, lineTotals.taxableBase.amount)
        },
        taxTotal = totals.taxTotal.amount,
        discountTotal = totals.invoiceDiscountAmount.amount,
        currency = currency.value,
        notes = invoice.notes.takeIf { it.isNotBlank() },
        footer = null,
    )
}
