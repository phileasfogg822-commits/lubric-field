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
 * Phase 21.3 — the invoice → posting builder: the first REAL
 * consumer of the Phase 20 posting engine. Pure: it turns an
 * invoice (header + lines) into a [DocumentPosting] that
 * satisfies [com.lubrcapp.lubrcapp.domain.accounting.PostingValidator]
 * by construction — totals come from [SalesDocumentEngine], so
 * grand-total, tax-total, and taxable-base reconciliation are
 * automatic, and the journal balances by construction (see
 * below). The caller supplies the account mapping: account
 * codes are master data, never invented here (the 16.x
 * nullable-rate lesson applied to accounts).
 *
 * Journal shape for a sale invoice:
 * - Dr Accounts Receivable = grandTotal (what the customer owes).
 * - Cr Revenue = grandTotal − taxTotal (net of tax).
 * - Cr Tax Payable = taxTotal, only when a tax account is
 *   supplied and tax is non-zero. Without a tax account the
 *   tax folds into revenue, so the journal still balances.
 * - Zero-amount lines are omitted (a zero-zero line would
 *   trip JOURNAL_LINE_INVALID); a zero-total invoice posts
 *   an empty journal, which still validates.
 * - Additional charges (§54) fold into the revenue line: a
 *   phase that needs a separate charges account extends
 *   [InvoicePostingAccounts].
 * - A bottom-line discount applies to tax-inclusive totals
 *   (17.3 step 5: tax is NOT recomputed), so a discount larger
 *   than the pre-tax base can push the revenue line negative
 *   while the journal still balances. Discount-before-tax
 *   semantics are a contract decision for a later phase, not
 *   something this builder silently revises.
 *
 * Stock movements: one OUT movement per stocked line with a
 * positive quantity (service lines carry no stock; zero
 * quantities move nothing). The app layer posts the built
 * posting through AccountsViewModel.postDocument (20.2) and
 * flips the invoice's serials through
 * InventoryViewModel.markSerialsSold (this phase) — the
 * cross-module call lives with the 21.5 screens, not here:
 * feature modules do not depend on each other.
 * Transaction id = the invoice id, so re-posting an invoice
 * overwrites its entry (20.2's "je-<transactionId>" rule)
 * instead of double-posting.
 */
data class InvoicePostingAccounts(
    val receivableAccountId: String,
    val revenueAccountId: String,
    val taxAccountId: String? = null,
)

object SalesInvoicePosting {

    fun invoiceToPosting(
        invoice: SalesDocument,
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
        accounts: InvoicePostingAccounts,
        charges: List<AdditionalChargeInput> = emptyList(),
        invoiceDiscount: Discount? = null,
        roundOff: Boolean = false,
    ): DocumentPosting {
        require(invoice.type == SalesDocumentType.INVOICE) { "Not an invoice: ${invoice.id}" }
        require(lines.isNotEmpty()) { "An invoice posting needs at least one line: ${invoice.id}" }
        require(lines.all { it.quantity.signum() >= 0 }) {
            "Sale invoice lines must not be negative; returns are 21.4: ${invoice.id}"
        }
        val totals = SalesDocumentEngine.totals(lines, currency, charges, invoiceDiscount, roundOff)
        val zero = Money.zero(currency)
        val journalLines = buildList {
            if (!totals.grandTotal.isZero) {
                add(PostingJournalLine(AccountId(accounts.receivableAccountId), totals.grandTotal, zero))
            }
            val revenueAmount = if (accounts.taxAccountId != null) {
                totals.grandTotal - totals.taxTotal
            } else {
                totals.grandTotal
            }
            if (!revenueAmount.isZero) {
                add(PostingJournalLine(AccountId(accounts.revenueAccountId), zero, revenueAmount))
            }
            if (accounts.taxAccountId != null && !totals.taxTotal.isZero) {
                add(PostingJournalLine(AccountId(accounts.taxAccountId), zero, totals.taxTotal))
            }
        }
        val stockMovements = lines.mapNotNull { line ->
            val itemId = line.itemId ?: return@mapNotNull null
            if (line.quantity.signum() <= 0) return@mapNotNull null
            StockMovement(
                itemId = ItemId(itemId),
                warehouseId = line.warehouseId?.let { WarehouseId(it) },
                kind = StockMovementKind.OUT,
                quantity = Quantity.of(line.quantity),
                rate = Money.of(line.rate, currency),
                batchId = line.batchId,
            )
        }
        return DocumentPosting(
            transactionId = TransactionId(invoice.id),
            documentType = "INVOICE",
            documentNumber = invoice.number,
            partyId = invoice.partyId?.let { PartyId(it) },
            totals = totals,
            stockMovements = stockMovements,
            journalLines = journalLines,
            partyEffect = invoice.partyId?.let {
                PartyLedgerEffect(PartyId(it), PartyBalanceDirection.RECEIVABLE, totals.grandTotal)
            },
        )
    }
}
