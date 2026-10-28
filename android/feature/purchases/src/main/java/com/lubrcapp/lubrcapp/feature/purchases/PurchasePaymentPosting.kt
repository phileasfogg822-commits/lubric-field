package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineTotals
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PartyLedgerEffect
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import com.lubrcapp.lubrcapp.domain.payment.Payment

/**
 * Phase 31.2 — the supplier-payment posting builder, the vendor
 * mirror of feature/sales' SalesPaymentPosting. The payment
 * document's totals carry ONE synthetic line — the payment
 * itself — because PostingValidator requires non-empty
 * totals.lines. Journal: Dr Payable, Cr Cash/Bank (the mirror
 * of Dr Cash, Cr Receivable). Party effect PAYABLE with a
 * NEGATIVE amount (payment reduces the payable; the credit
 * check only fires on positive effects, so it skips). No
 * stock movements: payments move no stock. TransactionId
 * from the payment id, so re-post overwrites via the 20.2
 * "je-<transactionId>" rule.
 */
data class PurchasePaymentPostingAccounts(
    val cashAccountId: String,
    val payableAccountId: String,
)

object PurchasePaymentPosting {

    fun paymentToPosting(
        payment: Payment,
        documentNumber: String,
        accounts: PurchasePaymentPostingAccounts,
    ): DocumentPosting {
        val amount = payment.amount
        val zero = Money.zero(amount.currency)
        val line = DocumentLineTotals(
            grossAmount = amount,
            discountAmount = zero,
            taxableBase = amount,
            taxComponents = emptyList(),
            totalTax = zero,
            lineTotal = amount,
        )
        val totals = DocumentTotals(
            lines = listOf(line),
            charges = emptyList(),
            grossBeforeDiscounts = amount,
            lineDiscountsTotal = zero,
            taxableBaseTotal = amount,
            taxComponents = emptyList(),
            taxTotal = zero,
            chargesTotal = zero,
            invoiceDiscountAmount = zero,
            roundOffAmount = zero,
            grandTotal = amount,
        )
        val journalLines = listOf(
            PostingJournalLine(AccountId(accounts.payableAccountId), amount, zero),
            PostingJournalLine(AccountId(accounts.cashAccountId), zero, amount),
        )
        return DocumentPosting(
            transactionId = TransactionId(payment.id.value),
            documentType = "PAYMENT",
            documentNumber = documentNumber,
            partyId = payment.partyId,
            totals = totals,
            stockMovements = emptyList(),
            journalLines = journalLines,
            partyEffect = PartyLedgerEffect(
                payment.partyId,
                PartyBalanceDirection.PAYABLE,
                -amount,
            ),
        )
    }
}
