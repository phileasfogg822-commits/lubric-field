package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerRecord
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentEngine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel

/**
 * Phase 22.5 — the 21.x backfill trigger, the app-layer
 * orchestration 22.3 deferred. Postings made before 22.3
 * have journal entries but no party_ledger rows (22.3's
 * postDocument writes rows only for postings made after
 * it landed). This rebuilds those rows from the stored
 * documents — the same writer, idempotent by entry id,
 * so re-running it overwrites instead of duplicating.
 *
 * Matching is by JOURNAL ENTRY ID, not by document
 * number: PostingEngine names every entry
 * "je-<transactionId>", and invoice / return /
 * credit-note postings carry transactionId = document id
 * (21.3, 21.4) — so "je-inv-1" IS document "inv-1".
 * Number matching would be ambiguous for unnumbered
 * documents (blank numbers collide); entry-id matching
 * has no such case. Payment / refund entries need no
 * backfill: no payment posting could have been
 * persisted before 22.3 (the payment builder landed in
 * 22.2 but no handler posts payments), and every
 * posting since 22.3 already writes its row.
 *
 * The effect is derived, not rebuilt through the
 * posting builders: the builders need account mappings
 * the journal does not store, but the effect needs only
 * the grand total and the document type — invoice adds,
 * return and credit note subtract. The totals come from
 * the document's lines with the handler's own defaults
 * (no charges, no invoice discount, no round-off: 21.5's
 * handler passes none), so the rebuilt total reproduces
 * the original posting's total exactly.
 *
 * Currency is a caller parameter: neither the journal
 * entry nor the document carries one, so the caller
 * supplies the business currency (the same value the
 * posting handler receives from its screen).
 *
 * Entries that match no document, or whose document has
 * no party, are skipped and counted — never errored. A
 * zero-total document still writes its (zero) row, the
 * same as 22.3's writer does for a zero effect: zero
 * rows are balance-neutral and one row per posting
 * effect keeps the ledger complete.
 */
object PartyLedgerBackfill {

    data class BackfillResult(val written: Int, val skipped: Int)

    suspend fun backfill(
        scope: OperationalScope,
        currency: CurrencyCode,
        salesViewModel: SalesViewModel,
        accountsViewModel: AccountsViewModel,
    ): BackfillResult {
        val documents = (
            salesViewModel.loadInvoices(scope) +
                salesViewModel.loadReturns(scope) +
                salesViewModel.loadCreditNotes(scope)
            ).associateBy { it.id }
        var written = 0
        var skipped = 0
        for (entry in accountsViewModel.loadJournalEntries(scope)) {
            if (!entry.id.startsWith("je-")) {
                skipped++
                continue
            }
            val document = documents[entry.id.removePrefix("je-")]
            val partyId = document?.partyId
            if (document == null || partyId == null) {
                skipped++
                continue
            }
            val isReversal = document.type == SalesDocumentType.SALES_RETURN ||
                document.type == SalesDocumentType.CREDIT_NOTE
            if (document.type != SalesDocumentType.INVOICE && !isReversal) {
                skipped++
                continue
            }
            val lines = salesViewModel.loadLines(scope, document.id)
            val grandTotal = SalesDocumentEngine.totals(lines, currency).grandTotal
            val amount = if (document.type == SalesDocumentType.INVOICE) {
                grandTotal.amount
            } else {
                grandTotal.amount.negate()
            }
            accountsViewModel.savePartyLedgerRow(
                scope,
                PartyLedgerRecord(
                    id = entry.id,
                    entryId = entry.id,
                    partyId = partyId,
                    direction = "RECEIVABLE",
                    amount = amount,
                    currency = currency.value,
                    transactionId = document.id,
                    documentType = document.type.name,
                    documentNumber = document.number,
                    date = entry.date,
                    description = entry.description,
                    createdAt = entry.createdAt,
                ),
            )
            written++
        }
        return BackfillResult(written, skipped)
    }
}
