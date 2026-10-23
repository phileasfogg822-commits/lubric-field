package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostingValidatorTest {
    private val pkr = CurrencyCode.of("PKR")
    private val party = PartyId("party-1")
    private val receivable = AccountId("acc-receivable")
    private val sales = AccountId("acc-sales")
    private val taxPayable = AccountId("acc-tax")
    private val salesTaxExclusive = TaxScheme(
        name = "Sales Tax",
        inclusive = false,
        components = listOf(TaxComponent("Sales Tax", Percentage.of("17"))),
    )

    /** A valid credit sale: 2 × 500 at 17% exclusive = 1170, fully balanced. */
    private fun validPosting(): DocumentPosting {
        val totals = DocumentTotalsCalculator.totalDocument(
            lines = listOf(
                DocumentLineInput(Quantity.of("2"), Money.of("500", pkr), taxScheme = salesTaxExclusive)
            )
        )
        return DocumentPosting(
            transactionId = TransactionId("txn-1"),
            documentType = "INVOICE",
            documentNumber = "INV-2026-00001",
            partyId = party,
            totals = totals,
            stockMovements = listOf(
                StockMovement(ItemId("item-1"), kind = StockMovementKind.OUT, quantity = Quantity.of("2"))
            ),
            journalLines = listOf(
                PostingJournalLine(receivable, Money.of("1170", pkr), Money.zero(pkr)),
                PostingJournalLine(sales, Money.zero(pkr), Money.of("1000", pkr)),
                PostingJournalLine(taxPayable, Money.zero(pkr), Money.of("170", pkr)),
            ),
            partyEffect = PartyLedgerEffect(party, PartyBalanceDirection.RECEIVABLE, Money.of("1170", pkr)),
        )
    }

    @Test
    fun aBalancedReconciledPostingHasNoViolations() {
        assertEquals(emptyList<PostingViolation>(), PostingValidator.validate(validPosting()))
    }

    @Test
    fun anUnbalancedJournalIsFlagged() {
        val posting = validPosting().copy(
            journalLines = listOf(PostingJournalLine(receivable, Money.of("1170", pkr), Money.zero(pkr)))
        )
        assertTrue(PostingValidator.validate(posting).contains(PostingViolation.JOURNAL_UNBALANCED))
    }

    @Test
    fun aJournalLineWithBothSidesSetOrNeitherSetIsFlagged() {
        val both = validPosting().copy(
            journalLines = listOf(
                PostingJournalLine(receivable, Money.of("100", pkr), Money.of("100", pkr)),
            )
        )
        assertTrue(PostingValidator.validate(both).contains(PostingViolation.JOURNAL_LINE_INVALID))

        val neither = validPosting().copy(
            journalLines = listOf(
                PostingJournalLine(receivable, Money.zero(pkr), Money.zero(pkr)),
            )
        )
        assertTrue(PostingValidator.validate(neither).contains(PostingViolation.JOURNAL_LINE_INVALID))
    }

    @Test
    fun anEmptyDocumentIsFlagged() {
        val emptyTotals = DocumentTotalsCalculator.totalDocument(
            lines = emptyList(),
            currencyHint = Money.zero(pkr),
        )
        val posting = validPosting().copy(totals = emptyTotals, journalLines = emptyList())
        assertTrue(PostingValidator.validate(posting).contains(PostingViolation.EMPTY_DOCUMENT))
    }

    @Test
    fun tamperedTotalsAreFlagged() {
        val base = validPosting()
        val badGrand = base.copy(totals = base.totals.copy(grandTotal = Money.of("1171", pkr)))
        assertTrue(PostingValidator.validate(badGrand).contains(PostingViolation.GRAND_TOTAL_MISMATCH))

        val badTax = base.copy(totals = base.totals.copy(taxTotal = Money.of("171", pkr)))
        assertTrue(PostingValidator.validate(badTax).contains(PostingViolation.TAX_TOTAL_MISMATCH))

        val badBase = base.copy(totals = base.totals.copy(taxableBaseTotal = Money.of("999", pkr)))
        assertTrue(PostingValidator.validate(badBase).contains(PostingViolation.TAXABLE_BASE_MISMATCH))
    }

    @Test
    fun aNonPositiveStockMovementIsFlagged() {
        val posting = validPosting().copy(
            stockMovements = listOf(
                StockMovement(ItemId("item-1"), kind = StockMovementKind.OUT, quantity = Quantity.ZERO)
            )
        )
        assertTrue(PostingValidator.validate(posting).contains(PostingViolation.STOCK_MOVEMENT_INVALID))
    }

    @Test
    fun aPartyEffectNamingAnotherPartyIsFlagged() {
        val posting = validPosting().copy(
            partyEffect = PartyLedgerEffect(PartyId("someone-else"), PartyBalanceDirection.RECEIVABLE, Money.of("1170", pkr))
        )
        assertTrue(PostingValidator.validate(posting).contains(PostingViolation.PARTY_EFFECT_MISMATCH))
    }

    @Test
    fun creditBlockAndLimitAreEnforcedFromCallerSuppliedFacts() {
        val posting = validPosting()

        val blocked = PostingValidator.validate(
            posting,
            CreditCheck(outstandingBefore = Money.zero(pkr), creditLimit = null, blocked = true),
        )
        assertTrue(blocked.contains(PostingViolation.CREDIT_BLOCKED))

        val overLimit = PostingValidator.validate(
            posting,
            CreditCheck(outstandingBefore = Money.of("900", pkr), creditLimit = Money.of("2000", pkr), blocked = false),
        )
        assertTrue(overLimit.contains(PostingViolation.CREDIT_LIMIT_EXCEEDED))

        val withinLimit = PostingValidator.validate(
            posting,
            CreditCheck(outstandingBefore = Money.of("500", pkr), creditLimit = Money.of("2000", pkr), blocked = false),
        )
        assertEquals(emptyList<PostingViolation>(), withinLimit)

        val noFactsSupplied = PostingValidator.validate(posting, creditCheck = null)
        assertEquals(emptyList<PostingViolation>(), noFactsSupplied)
    }
}
