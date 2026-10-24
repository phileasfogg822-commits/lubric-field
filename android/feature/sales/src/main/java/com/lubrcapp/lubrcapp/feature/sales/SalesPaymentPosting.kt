package com.lubrcapp.lubrcapp.feature.sales

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
 * Payment posting builder, mirroring the 21.3 invoice builder.
 * Target home: feature/sales, next to SalesInvoicePosting —
 * integration adds a `:domain:payment` dependency to
 * feature/sales (it already depends on `:domain:accounting`
 * and `:domain:business`).
 *
 * The payment document's totals carry ONE synthetic line —
 * the payment itself — because PostingValidator requires
 * non-empty totals.lines and recomputes grandTotal from
 * them. The line is the payment, so the representation is
 * honest and the validator needs no change. Journal: Dr
 * Cash/Bank, Cr Receivable. Party effect RECEIVABLE with a
 * NEGATIVE amount (payment reduces the receivable; the
 * chain is sign-agnostic per the 21.4 pin, and the credit
 * check only fires on positive effects, so it skips).
 * No stock movements: payments move no stock.
 * TransactionId from the payment id, so re-post overwrites
 * via the 20.2 "je-<transactionId>" rule.
 */
data class PaymentPostingAccounts(
    val cashAccountId: String,
    val receivableAccountId: String,
)

object SalesPaymentPosting {

    fun paymentToPosting(
        payment: Payment,
        documentNumber: String,
        accounts: PaymentPostingAccounts,
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
            PostingJournalLine(AccountId(accounts.cashAccountId), amount, zero),
            PostingJournalLine(AccountId(accounts.receivableAccountId), zero, amount),
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
                PartyBalanceDirection.RECEIVABLE,
                -amount,
            ),
        )
    }
}
