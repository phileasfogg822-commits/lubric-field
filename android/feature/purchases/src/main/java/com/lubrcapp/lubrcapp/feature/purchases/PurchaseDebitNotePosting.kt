package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.domain.accounting.Discount
import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineInput
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotalsCalculator
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PartyLedgerEffect
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.accounting.StockMovement
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.accounting.TaxComponent
import com.lubrcapp.lubrcapp.domain.accounting.TaxScheme
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TransactionId

/**
 * Phase 23.5 (STAGED — integrates after 23.4 accepts) —
 * the debit-note posting builder, the vendor mirror
 * of 21.4's sales-return builder (supervisor
 * decision 5). A return to supplier reverses a
 * purchase with POSITIVE amounts and a side-swapped
 * journal: Dr Payable / Cr Expense / Cr Tax, with a
 * NEGATIVE PAYABLE effect for the full grand total
 * (the ledger truth: the payable goes down), and
 * stock OUT movements for the returned lines.
 *
 * A debit note is its own document, not a purchase
 * invoice, so it carries its own line type:
 * [DebitNoteLine] holds only what the posting
 * needs (item, positive returned quantity, price,
 * tax rate, line discount) and no purchase linkage
 * — the join owns mapping a debit note back to the
 * purchase it reverses. Lines are positive by
 * contract (21.4's rule: returns carry positive
 * returned quantities); a negative quantity is a
 * caller error, not a sign convention.
 *
 * Totals are recomputed from the lines through the
 * shared DocumentTotalsCalculator, exactly as in
 * 23.2, so the posting satisfies PostingValidator
 * by construction. No bottom-line discount: a debit
 * note reverses line value, and discount handling
 * belongs to the join if a case ever needs it.
 * Posting does not change any purchase status.
 */
data class DebitNoteLine(
    val id: String,
    val itemId: String,
    val description: String = "",
    val quantity: Quantity,
    val unitPrice: Money,
    val taxRate: Percentage = Percentage.ZERO,
    val discount: Money,
)

data class DebitNotePostingAccounts(
    val payableAccountId: String,
    val expenseAccountId: String,
    val taxAccountId: String? = null,
)

object PurchaseDebitNotePosting {

    fun debitNoteToPosting(
        debitNoteId: String,
        documentNumber: String,
        partyId: PartyId,
        lines: List<DebitNoteLine>,
        accounts: DebitNotePostingAccounts,
    ): DocumentPosting {
        require(lines.isNotEmpty()) {
            "A debit note posting needs at least one line: $debitNoteId"
        }
        require(lines.all { it.quantity.amount.signum() >= 0 }) {
            "Debit note lines carry positive returned quantities: $debitNoteId"
        }
        val inputs = lines.map { line ->
            DocumentLineInput(
                quantity = line.quantity,
                rate = line.unitPrice,
                discount = if (line.discount.isZero) null else Discount.Fixed(line.discount),
                taxScheme = if (line.taxRate == Percentage.ZERO) {
                    null
                } else {
                    TaxScheme(
                        name = "Purchase Tax",
                        inclusive = false,
                        components = listOf(TaxComponent("Tax", line.taxRate)),
                    )
                },
            )
        }
        val totals = DocumentTotalsCalculator.totalDocument(lines = inputs)
        val zero = Money.zero(totals.grandTotal.currency)
        val journalLines = buildList {
            if (!totals.grandTotal.isZero) {
                add(PostingJournalLine(AccountId(accounts.payableAccountId), totals.grandTotal, zero))
                val expenseAmount = if (accounts.taxAccountId != null) {
                    totals.grandTotal - totals.taxTotal
                } else {
                    totals.grandTotal
                }
                if (!expenseAmount.isZero) {
                    add(PostingJournalLine(AccountId(accounts.expenseAccountId), zero, expenseAmount))
                }
                if (accounts.taxAccountId != null && !totals.taxTotal.isZero) {
                    add(PostingJournalLine(AccountId(accounts.taxAccountId), zero, totals.taxTotal))
                }
            }
        }
        val stockMovements = lines.mapNotNull { line ->
            if (line.quantity.amount.signum() <= 0) return@mapNotNull null
            StockMovement(
                itemId = ItemId(line.itemId),
                warehouseId = null,
                kind = StockMovementKind.OUT,
                quantity = line.quantity,
                rate = line.unitPrice,
            )
        }
        return DocumentPosting(
            transactionId = TransactionId(debitNoteId),
            documentType = "DEBIT_NOTE",
            documentNumber = documentNumber,
            partyId = partyId,
            totals = totals,
            stockMovements = stockMovements,
            journalLines = journalLines,
            partyEffect = PartyLedgerEffect(
                partyId,
                PartyBalanceDirection.PAYABLE,
                zero - totals.grandTotal,
            ),
        )
    }
}
