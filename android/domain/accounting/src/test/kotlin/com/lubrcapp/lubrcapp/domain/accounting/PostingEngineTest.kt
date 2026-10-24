package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostingEngineTest {

    private val pkr = CurrencyCode.of("PKR")
    private val date = LocalDate.parse("2026-10-24")
    private val now = Instant.parse("2026-10-24T00:00:00Z")

    private fun money(amount: String): Money = Money.of(amount, pkr)

    private fun totals(grandTotal: String = "100000"): DocumentTotals {
        val line = DocumentLineTotals(
            grossAmount = money("100000"),
            discountAmount = money("0"),
            taxableBase = money("100000"),
            taxComponents = emptyList(),
            totalTax = money("0"),
            lineTotal = money("100000"),
        )
        return DocumentTotals(
            lines = listOf(line),
            charges = emptyList(),
            grossBeforeDiscounts = money("100000"),
            lineDiscountsTotal = money("0"),
            taxableBaseTotal = money("100000"),
            taxComponents = emptyList(),
            taxTotal = money("0"),
            chargesTotal = money("0"),
            invoiceDiscountAmount = money("0"),
            roundOffAmount = money("0"),
            grandTotal = money(grandTotal),
        )
    }

    private fun creditSalePosting(
        journalLines: List<PostingJournalLine> = listOf(
            PostingJournalLine(AccountId("receivable"), money("100000"), Money.zero(pkr)),
            PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("100000")),
        ),
        partyId: PartyId? = null,
        partyEffect: PartyLedgerEffect? = null,
    ): DocumentPosting = DocumentPosting(
        transactionId = TransactionId("tx-1"),
        documentType = "INVOICE",
        documentNumber = "INV-2026-00001",
        partyId = partyId,
        totals = totals(),
        journalLines = journalLines,
        partyEffect = partyEffect,
    )

    private fun post(
        posting: DocumentPosting,
        creditCheck: CreditCheck? = null,
    ): PostingResult = PostingEngine.post(
        posting = posting,
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        date = date,
        now = now,
        deviceId = DeviceId("device-a"),
        creditCheck = creditCheck,
    )

    @Test
    fun validPostingProducesBalancedEntryWithDerivedId() {
        val result = post(creditSalePosting())
        assertTrue(result is PostingResult.Posted)
        val entry = (result as PostingResult.Posted).entry
        assertEquals("je-tx-1", entry.id.value)
        assertEquals("INV-2026-00001", entry.referenceNumber)
        assertEquals("2026-10-24", entry.date.toString())
        assertEquals(2, entry.lines.size)
        assertEquals("je-tx-1-0", entry.lines[0].id.value)
        assertEquals("receivable", entry.lines[0].accountId.value)
        val debits = entry.lines.fold(Money.zero(pkr)) { acc, line -> acc + line.debit }
        val credits = entry.lines.fold(Money.zero(pkr)) { acc, line -> acc + line.credit }
        assertEquals(debits, credits)
    }

    @Test
    fun unbalancedJournalLinesAreRejected() {
        val result = post(
            creditSalePosting(
                journalLines = listOf(
                    PostingJournalLine(AccountId("receivable"), money("100000"), Money.zero(pkr)),
                    PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("99999")),
                ),
            ),
        )
        assertTrue(result is PostingResult.Rejected)
        assertEquals(
            listOf(PostingViolation.JOURNAL_UNBALANCED),
            (result as PostingResult.Rejected).violations,
        )
    }

    @Test
    fun emptyDocumentIsRejected() {
        val empty = DocumentPosting(
            transactionId = TransactionId("tx-2"),
            documentType = "INVOICE",
            documentNumber = "INV-2026-00002",
            totals = DocumentTotals(
                lines = emptyList(),
                charges = emptyList(),
                grossBeforeDiscounts = money("0"),
                lineDiscountsTotal = money("0"),
                taxableBaseTotal = money("0"),
                taxComponents = emptyList(),
                taxTotal = money("0"),
                chargesTotal = money("0"),
                invoiceDiscountAmount = money("0"),
                roundOffAmount = money("0"),
                grandTotal = money("0"),
            ),
        )
        val result = post(empty)
        assertTrue(result is PostingResult.Rejected)
        assertTrue(
            (result as PostingResult.Rejected).violations.contains(PostingViolation.EMPTY_DOCUMENT),
        )
    }

    @Test
    fun twoSidedLineIsRejected() {
        val result = post(
            creditSalePosting(
                journalLines = listOf(
                    PostingJournalLine(AccountId("receivable"), money("100000"), money("50000")),
                    PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("50000")),
                ),
            ),
        )
        assertTrue(result is PostingResult.Rejected)
        assertTrue(
            (result as PostingResult.Rejected).violations.contains(PostingViolation.JOURNAL_LINE_INVALID),
        )
    }

    @Test
    fun creditBlockedPartyIsRejected() {
        val party = PartyId("party-1")
        val result = post(
            creditSalePosting(
                partyId = party,
                partyEffect = PartyLedgerEffect(party, PartyBalanceDirection.RECEIVABLE, money("100000")),
            ),
            creditCheck = CreditCheck(
                outstandingBefore = money("0"),
                creditLimit = null,
                blocked = true,
            ),
        )
        assertTrue(result is PostingResult.Rejected)
        assertTrue(
            (result as PostingResult.Rejected).violations.contains(PostingViolation.CREDIT_BLOCKED),
        )
    }

    @Test
    fun creditLimitExceededIsRejected() {
        val party = PartyId("party-1")
        val result = post(
            creditSalePosting(
                partyId = party,
                partyEffect = PartyLedgerEffect(party, PartyBalanceDirection.RECEIVABLE, money("100000")),
            ),
            creditCheck = CreditCheck(
                outstandingBefore = money("0"),
                creditLimit = money("50000"),
                blocked = false,
            ),
        )
        assertTrue(result is PostingResult.Rejected)
        assertTrue(
            (result as PostingResult.Rejected).violations.contains(PostingViolation.CREDIT_LIMIT_EXCEEDED),
        )
    }
}
