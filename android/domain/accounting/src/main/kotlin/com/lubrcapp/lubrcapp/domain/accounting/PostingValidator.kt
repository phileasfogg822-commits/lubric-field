package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.Money

enum class PostingViolation {
    /** The document has no lines; a posted document must bill something. */
    EMPTY_DOCUMENT,

    /** Total debits do not equal total credits. */
    JOURNAL_UNBALANCED,

    /** A journal line has both sides set, or neither. */
    JOURNAL_LINE_INVALID,

    /** A stock movement carries a non-positive quantity. */
    STOCK_MOVEMENT_INVALID,

    /** grandTotal does not reconcile with lines − discount + charges + round-off. */
    GRAND_TOTAL_MISMATCH,

    /** taxTotal does not equal the sum of the aggregated tax components. */
    TAX_TOTAL_MISMATCH,

    /** taxableBaseTotal does not equal the sum of line and charge bases. */
    TAXABLE_BASE_MISMATCH,

    /** A party effect exists without a party, or names a different party. */
    PARTY_EFFECT_MISMATCH,

    /** The party is credit-blocked (master spec §18). */
    CREDIT_BLOCKED,

    /** The posting would push the party's outstanding over its credit limit (§18). */
    CREDIT_LIMIT_EXCEEDED,
}

/**
 * Caller-supplied credit facts for the §18 hard block. Phase 14 defined
 * the credit rules over master data; the posting gate needs the CURRENT
 * outstanding and limit, which only the caller (a document phase with
 * ledger access) can supply — so they arrive as inputs, and the rule
 * itself is enforced here, in one place, for every document type.
 */
data class CreditCheck(
    val outstandingBefore: Money,
    val creditLimit: Money?,
    val blocked: Boolean,
)

object PostingValidator {

    fun validate(posting: DocumentPosting, creditCheck: CreditCheck? = null): List<PostingViolation> {
        val violations = mutableListOf<PostingViolation>()
        val totals = posting.totals
        val currency = totals.grandTotal.currency
        val zero = Money.zero(currency)

        if (totals.lines.isEmpty()) violations += PostingViolation.EMPTY_DOCUMENT

        val totalDebits = posting.journalLines.fold(zero) { acc, line -> acc + line.debit }
        val totalCredits = posting.journalLines.fold(zero) { acc, line -> acc + line.credit }
        if (totalDebits != totalCredits) violations += PostingViolation.JOURNAL_UNBALANCED
        if (posting.journalLines.any { line ->
                (line.debit.isZero && line.credit.isZero) || (!line.debit.isZero && !line.credit.isZero)
            }
        ) {
            violations += PostingViolation.JOURNAL_LINE_INVALID
        }

        if (posting.stockMovements.any { !it.quantity.isPositive }) {
            violations += PostingViolation.STOCK_MOVEMENT_INVALID
        }

        val linesTotal = totals.lines.fold(zero) { acc, line -> acc + line.lineTotal }
        val recomputedGrand = linesTotal - totals.invoiceDiscountAmount + totals.chargesTotal + totals.roundOffAmount
        if (recomputedGrand != totals.grandTotal) violations += PostingViolation.GRAND_TOTAL_MISMATCH

        val componentTaxTotal = totals.taxComponents.fold(zero) { acc, part -> acc + part.amount }
        if (componentTaxTotal != totals.taxTotal) violations += PostingViolation.TAX_TOTAL_MISMATCH

        val recomputedBase =
            totals.lines.fold(zero) { acc, line -> acc + line.taxableBase } +
                totals.charges.fold(zero) { acc, charge -> acc + charge.taxableBase }
        if (recomputedBase != totals.taxableBaseTotal) violations += PostingViolation.TAXABLE_BASE_MISMATCH

        val effect = posting.partyEffect
        if (effect != null && (posting.partyId == null || effect.partyId != posting.partyId)) {
            violations += PostingViolation.PARTY_EFFECT_MISMATCH
        }

        if (creditCheck != null && effect != null &&
            effect.direction == PartyBalanceDirection.RECEIVABLE && effect.amount.isPositive
        ) {
            val limit = creditCheck.creditLimit
            if (creditCheck.blocked) {
                violations += PostingViolation.CREDIT_BLOCKED
            } else if (limit != null && creditCheck.outstandingBefore + effect.amount > limit) {
                violations += PostingViolation.CREDIT_LIMIT_EXCEEDED
            }
        }

        return violations
    }
}
