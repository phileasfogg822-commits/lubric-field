package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PostingValidator
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesRefundPostingTest {

    private val pkr = CurrencyCode.of("PKR")

    @Test
    fun `refund posting balances validates and clears toward zero`() {
        val posting = SalesRefundPosting.refundToPosting(
            refundId = "ref-1",
            partyId = PartyId("party-1"),
            amount = Money.of("500", pkr),
            documentNumber = "SR-2026-00001",
            accounts = RefundPostingAccounts("cash-1", "recv-1"),
        )
        assertEquals("REFUND", posting.documentType)
        assertEquals("SR-2026-00001", posting.documentNumber)
        assertEquals(Money.of("500", pkr), posting.totals.grandTotal)
        assertEquals(2, posting.journalLines.size)
        // Dr Receivable first (the credit balance clears),
        // Cr Cash second (the money leaves).
        assertEquals(AccountId("recv-1"), posting.journalLines[0].accountId)
        assertEquals(Money.of("500", pkr), posting.journalLines[0].debit)
        assertTrue(posting.journalLines[0].credit.isZero)
        assertEquals(AccountId("cash-1"), posting.journalLines[1].accountId)
        assertTrue(posting.journalLines[1].debit.isZero)
        assertEquals(Money.of("500", pkr), posting.journalLines[1].credit)
        assertEquals("ref-1", posting.transactionId.value)
        assertEquals(PartyId("party-1"), posting.partyId)
        assertTrue(posting.stockMovements.isEmpty())
        val effect = posting.partyEffect
        assertEquals(PartyBalanceDirection.RECEIVABLE, effect?.direction)
        // POSITIVE: a refund raises a negative (credit)
        // balance back toward zero — the opposite sign from
        // a payment's negative effect.
        assertEquals(Money.of("500", pkr), effect?.amount)
        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test
    fun `zero refund is rejected at build time`() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesRefundPosting.refundToPosting(
                refundId = "ref-1",
                partyId = PartyId("party-1"),
                amount = Money.of("0", pkr),
                documentNumber = "SR-2026-00001",
                accounts = RefundPostingAccounts("cash-1", "recv-1"),
            )
        }
    }

    @Test
    fun `negative refund is rejected at build time`() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesRefundPosting.refundToPosting(
                refundId = "ref-1",
                partyId = PartyId("party-1"),
                amount = Money.of("-500", pkr),
                documentNumber = "SR-2026-00001",
                accounts = RefundPostingAccounts("cash-1", "recv-1"),
            )
        }
    }
}
