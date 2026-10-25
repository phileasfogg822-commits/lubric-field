package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineTotals
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PartyLedgerEffect
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.TransactionId

/**
 * Cash-out refund posting builder, mirroring the 22.2
 * payment builder with sides swapped. A refund settles a
 * sales return's credit balance: the return already
 * credited Receivable (21.4's negative RECEIVABLE effect),
 * leaving the customer with a negative balance — money the
 * business owes back. Paying it out clears that balance
 * toward zero, so the journal is Dr Receivable / Cr Cash
 * with POSITIVE amounts (the 21.4 mirror principle: side
 * swaps use positive amounts, never negative lines), and
 * the party effect is RECEIVABLE with a POSITIVE amount
 * (the balance rises back toward zero).
 *
 * Scope, matching 22.2: builder only, no handler, no
 * screens (those are 22.5's job). The builder takes the
 * refund's fields directly: there is no Refund domain type
 * yet, and reusing Payment would conflate customer refunds
 * with vendor payments (PaymentDirection.PAID is the
 * payables side, Phase 23's work). The typed refund record
 * (method / reference / return link) lands with the 22.5
 * handler, same as payments' typed record question.
 * documentNumber is caller-supplied (the return's number
 * or a refund reference); refunds have no numbering
 * prefix (NumberingRecord covers QT/SO/INV/SR/CN only).
 * TransactionId from the refund id, so re-post overwrites
 * via the 20.2 "je-<transactionId>" rule.
 *
 * Two rules the caller must honor, both documented here
 * because the builder cannot see them:
 * 1. Post the return FIRST. The refund only makes sense
 *    against a credit balance the return created; the
 *    builder takes no balance snapshot, so over-refunding
 *    past zero is the handler's ordering job, not a
 *    builder check.
 * 2. Do NOT supply a validator creditCheck for refunds.
 *    The validator fires its credit check on any positive
 *    RECEIVABLE effect, and it cannot distinguish "new
 *    credit" from "clearing a credit balance" — but the
 *    §18 block fires only on new positive exposure, which
 *    a refund never creates (it moves a negative balance
 *    toward zero). A limit-derived check would wrongly
 *    block refunds to parties at their limit. The validator
 *    itself is NOT changed (same rule as 22.2).
 *
 * Credit-against-future-invoices is NOT in 22.4: leaving
 * the credit balance means a later invoice must consume
 * it, but 22.1 allocation allocates payments, not credit
 * balances — extending allocation is a separate design,
 * deferred to a later package. Until then the credit
 * balance carries forward per 22.3's rule.
 */
data class RefundPostingAccounts(
    val cashAccountId: String,
    val receivableAccountId: String,
)

object SalesRefundPosting {

    fun refundToPosting(
        refundId: String,
        partyId: PartyId,
        amount: Money,
        documentNumber: String,
        accounts: RefundPostingAccounts,
    ): DocumentPosting {
        require(amount.isPositive) {
            "A refund pays out a positive amount: $refundId"
        }
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
            PostingJournalLine(AccountId(accounts.receivableAccountId), amount, zero),
            PostingJournalLine(AccountId(accounts.cashAccountId), zero, amount),
        )
        return DocumentPosting(
            transactionId = TransactionId(refundId),
            documentType = "REFUND",
            documentNumber = documentNumber,
            partyId = partyId,
            totals = totals,
            stockMovements = emptyList(),
            journalLines = journalLines,
            partyEffect = PartyLedgerEffect(
                partyId,
                PartyBalanceDirection.RECEIVABLE,
                amount,
            ),
        )
    }
}
