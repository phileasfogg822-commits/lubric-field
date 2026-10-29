package com.lubrcapp.lubrcapp.feature.printing

import java.math.BigDecimal

/**
 * Phase 32.5 — themed print rows: the single row list both themed
 * renderers consume (the Compose preview scrolls it, the PDF writer
 * paginates it during drawing, repeating the table header on page
 * breaks inside a table). A row list is FLAT — pagination is the
 * renderer's job, because only the renderer knows its row heights.
 *
 * Tables carry their column weights so the PDF canvas and the
 * Compose Row agree on column positions. Cells from [TableRow]
 * .numericFrom on are numbers and render right-aligned.
 */
sealed interface ThemedRow {
    data class Meta(val text: String) : ThemedRow
    data class TableHeader(val columns: List<String>, val weights: List<Float>) : ThemedRow
    data class TableRow(
        val cells: List<String>,
        val weights: List<Float>,
        val numericFrom: Int = 1,
    ) : ThemedRow

    data class Total(val label: String, val value: String, val emphasized: Boolean = false) : ThemedRow
    data class Note(val text: String) : ThemedRow
    data object Blank : ThemedRow
}

val INVOICE_TABLE_WEIGHTS: List<Float> = listOf(0.46f, 0.14f, 0.18f, 0.22f)
val STATEMENT_TABLE_WEIGHTS: List<Float> = listOf(0.18f, 0.34f, 0.16f, 0.16f, 0.16f)

private fun moneyText(amount: BigDecimal, currency: String): String =
    if (currency.isBlank()) amount.toPlainString() else "${amount.toPlainString()} $currency"

/**
 * Themed rows for an invoice document. Totals carry the currency
 * suffix when the document has one; item cells stay bare numbers
 * so the table columns stay tight. Tax and discount rows appear
 * only when nonzero, matching the plain layout's rule.
 */
fun layoutThemedInvoice(document: PrintableDocument): List<ThemedRow> {
    val rows = mutableListOf<ThemedRow>()
    rows += ThemedRow.Meta("No: ${document.documentNumber}    Date: ${document.date}")
    if (document.partyName != null) {
        rows += ThemedRow.Meta("Party: ${document.partyName}")
    }
    rows += ThemedRow.Blank
    rows += ThemedRow.TableHeader(
        listOf("Item", "Qty", "Rate", "Amount"),
        INVOICE_TABLE_WEIGHTS,
    )
    document.lines.forEach { line ->
        rows += ThemedRow.TableRow(
            listOf(
                line.description,
                line.quantity.toPlainString(),
                line.rate.toPlainString(),
                line.amount.toPlainString(),
            ),
            INVOICE_TABLE_WEIGHTS,
        )
    }
    rows += ThemedRow.Total("Subtotal", moneyText(document.subtotal, document.currency))
    if (document.taxTotal.signum() != 0) {
        rows += ThemedRow.Total("Tax", moneyText(document.taxTotal, document.currency))
    }
    if (document.discountTotal.signum() != 0) {
        rows += ThemedRow.Total("Discount", moneyText(document.discountTotal, document.currency))
    }
    rows += ThemedRow.Total(
        "Total",
        moneyText(document.grandTotal, document.currency),
        emphasized = true,
    )
    if (document.notes != null) {
        rows += ThemedRow.Note("Notes: ${document.notes}")
    }
    if (document.footer != null) {
        rows += ThemedRow.Note(document.footer)
    }
    return rows
}

/** One statement line, already split: exactly one of debit/credit is set. */
data class StatementEntryInput(
    val date: String,
    val detail: String,
    val debit: BigDecimal?,
    val credit: BigDecimal?,
    val balance: BigDecimal,
)

/**
 * Themed rows for a per-customer statement. The caller owns the
 * debit/credit split and the running balances (the shell maps the
 * ledger profile's statement, whose amounts are signed: positive
 * is a charge, negative a payment). The closing balance is the
 * profile's net, emphasized. No opening-balance row: the profile
 * is ledger-only, and folding an opening balance in would silently
 * change every running balance it reports.
 */
fun layoutThemedStatement(
    partyName: String,
    currency: String,
    entries: List<StatementEntryInput>,
    closingBalance: BigDecimal,
): List<ThemedRow> {
    val rows = mutableListOf<ThemedRow>()
    rows += ThemedRow.Meta("Party: $partyName")
    rows += ThemedRow.Blank
    rows += ThemedRow.TableHeader(
        listOf("Date", "Detail", "Debit", "Credit", "Balance"),
        STATEMENT_TABLE_WEIGHTS,
    )
    entries.forEach { entry ->
        rows += ThemedRow.TableRow(
            listOf(
                entry.date,
                entry.detail,
                entry.debit?.toPlainString() ?: "",
                entry.credit?.toPlainString() ?: "",
                entry.balance.toPlainString(),
            ),
            STATEMENT_TABLE_WEIGHTS,
            numericFrom = 2,
        )
    }
    rows += ThemedRow.Total(
        "Closing balance",
        moneyText(closingBalance, currency),
        emphasized = true,
    )
    return rows
}
