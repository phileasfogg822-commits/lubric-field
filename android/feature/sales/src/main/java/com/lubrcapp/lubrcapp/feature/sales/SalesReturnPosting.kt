package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.AdditionalChargeInput
import com.lubrcapp.lubrcapp.domain.accounting.Discount
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PartyLedgerEffect
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.accounting.StockMovement
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import com.lubrcapp.lubrcapp.domain.common.WarehouseId

/**
 * Phase 21.4 — the return / credit-note → posting builders
 * (§64 Sales Return, §65 Credit Notes), the mirror of 21.3's
 * invoice builder. A return reverses a sale, so its journal
 * swaps sides with positive amounts instead of negating the
 * invoice's lines: negative journal lines would validate but
 * read wrong in every report.
 *
 * Journal shape for a return or credit note:
 * - Dr Revenue = grandTotal − taxTotal (the sale, undone).
 * - Dr Tax Payable = taxTotal, only when a tax account is
 *   supplied and tax is non-zero. Without a tax account the
 *   tax folds into revenue, so the journal still balances.
 * - Cr Accounts Receivable = grandTotal (the customer owes
 *   less). Zero-amount lines are omitted; a zero-total
 *   document posts an empty journal, which still validates.
 *
 * The two document types differ in stock, not in money:
 * - SALES_RETURN restores stock: one IN movement per stocked
 *   positive line (§64 "stock should be restored when
 *   appropriate"), and the app layer flips the returned
 *   serials SOLD → IN_STOCK through
 *   InventoryViewModel.markSerialsReturned (this phase).
 * - CREDIT_NOTE is financial only (§65 standalone or
 *   invoice-linked): it posts no stock movement and flips no
 *   serial. A credit note linked to a return is the return's
 *   own document; it never double-restores stock.
 *
 * The party effect is RECEIVABLE with a NEGATIVE amount: the
 * receivable balance falls by the returned total. Nothing
 * persists party effects yet (that is Phase 22's ledger job —
 * 20.2's postDocument does not write party_ledger rows), and
 * the validator checks only that the effect's party matches
 * the posting's party, so the negative amount is safe here.
 * Transaction id = the document id, so re-posting overwrites
 * (20.2's "je-<transactionId>" rule). Refund settlement
 * (the §64 Refund Mode field) is Phase 22's payments work:
 * 21.4 records the documents and their postings, not the
 * refund itself. Account mapping reuses [InvoicePostingAccounts]:
 * the same three accounts serve a sale and its reversal.
 */
object SalesReturnPosting {

    fun returnToPosting(
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
        accounts: InvoicePostingAccounts,
        charges: List<AdditionalChargeInput> = emptyList(),
        discount: Discount? = null,
        roundOff: Boolean = false,
    ): DocumentPosting {
        require(document.type == SalesDocumentType.SALES_RETURN) { "Not a sales return: ${document.id}" }
        return build(document, lines, currency, accounts, charges, discount, roundOff, restoreStock = true)
    }

    fun creditNoteToPosting(
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
        accounts: InvoicePostingAccounts,
        charges: List<AdditionalChargeInput> = emptyList(),
        discount: Discount? = null,
        roundOff: Boolean = false,
    ): DocumentPosting {
        require(document.type == SalesDocumentType.CREDIT_NOTE) { "Not a credit note: ${document.id}" }
        return build(document, lines, currency, accounts, charges, discount, roundOff, restoreStock = false)
    }

    private fun build(
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
        accounts: InvoicePostingAccounts,
        charges: List<AdditionalChargeInput>,
        discount: Discount?,
        roundOff: Boolean,
        restoreStock: Boolean,
    ): DocumentPosting {
        require(lines.isNotEmpty()) { "A return posting needs at least one line: ${document.id}" }
        require(lines.all { it.quantity.signum() >= 0 }) {
            "Return lines carry positive returned quantities: ${document.id}"
        }
        val totals = SalesDocumentEngine.totals(lines, currency, charges, discount, roundOff)
        val zero = Money.zero(currency)
        val journalLines = buildList {
            if (!totals.grandTotal.isZero) {
                val revenueAmount = if (accounts.taxAccountId != null) {
                    totals.grandTotal - totals.taxTotal
                } else {
                    totals.grandTotal
                }
                if (!revenueAmount.isZero) {
                    add(PostingJournalLine(AccountId(accounts.revenueAccountId), revenueAmount, zero))
                }
                if (accounts.taxAccountId != null && !totals.taxTotal.isZero) {
                    add(PostingJournalLine(AccountId(accounts.taxAccountId), totals.taxTotal, zero))
                }
                add(PostingJournalLine(AccountId(accounts.receivableAccountId), zero, totals.grandTotal))
            }
        }
        val stockMovements = if (!restoreStock) {
            emptyList()
        } else {
            lines.mapNotNull { line ->
                val itemId = line.itemId ?: return@mapNotNull null
                if (line.quantity.signum() <= 0) return@mapNotNull null
                StockMovement(
                    itemId = ItemId(itemId),
                    warehouseId = line.warehouseId?.let { WarehouseId(it) },
                    kind = StockMovementKind.IN,
                    quantity = Quantity.of(line.quantity),
                    rate = Money.of(line.rate, currency),
                    batchId = line.batchId,
                )
            }
        }
        return DocumentPosting(
            transactionId = TransactionId(document.id),
            documentType = document.type.name,
            documentNumber = document.number,
            partyId = document.partyId?.let { PartyId(it) },
            totals = totals,
            stockMovements = stockMovements,
            journalLines = journalLines,
            partyEffect = document.partyId?.let {
                PartyLedgerEffect(PartyId(it), PartyBalanceDirection.RECEIVABLE, zero - totals.grandTotal)
            },
        )
    }
}
