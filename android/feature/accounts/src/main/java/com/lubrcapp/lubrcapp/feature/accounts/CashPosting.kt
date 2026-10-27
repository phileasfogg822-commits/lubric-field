package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineTotals
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import java.util.UUID

/**
 * Phase 31.3 — the general cash book: cash in from / out to a
 * counterpart account, with no party attached. Customer and
 * supplier cash already flow through 31.2 with party effects;
 * this is the general book, not a second receipt path.
 *
 * Shape mirrors SalesPaymentPosting's synthetic-line pattern:
 * one synthetic line = the amount, because PostingValidator
 * requires non-empty totals.lines and recomputes grandTotal
 * from them. Journal: IN = Dr cash / Cr counterpart, OUT =
 * Dr counterpart / Cr cash. No stock movements, no party
 * effect (partyEffect is nullable and stays null — the
 * validator's party-effect check only fires when one is set).
 * documentType "CASH" is new: no existing builder uses it
 * (PAYMENT, REFUND, INVOICE and the sales enum names only).
 * TransactionId is a fresh UUID per entry, so each recorded
 * entry is its own journal document (the "je-<id>" rule).
 */
enum class CashDirection { IN, OUT }

data class CashPostingAccounts(
    val cashAccountId: String,
    val counterpartAccountId: String,
)

object CashPosting {

    fun cashToPosting(
        amount: Money,
        direction: CashDirection,
        accounts: CashPostingAccounts,
        documentNumber: String,
    ): DocumentPosting {
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
        val cashDebit = if (direction == CashDirection.IN) amount else zero
        val cashCredit = if (direction == CashDirection.IN) zero else amount
        val counterpartDebit = if (direction == CashDirection.IN) zero else amount
        val counterpartCredit = if (direction == CashDirection.IN) amount else zero
        val journalLines = listOf(
            PostingJournalLine(AccountId(accounts.cashAccountId), cashDebit, cashCredit),
            PostingJournalLine(AccountId(accounts.counterpartAccountId), counterpartDebit, counterpartCredit),
        )
        return DocumentPosting(
            transactionId = TransactionId(UUID.randomUUID().toString()),
            documentType = "CASH",
            documentNumber = documentNumber,
            partyId = null,
            totals = totals,
            stockMovements = emptyList(),
            journalLines = journalLines,
            partyEffect = null,
        )
    }
}
