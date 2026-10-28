package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashPostingTest {
    private val currency = CurrencyCode.of("PKR")
    private val accounts = CashPostingAccounts("cash", "sales")

    @Test fun cashInDebitsCashCreditsCounterpart() {
        val posting = CashPosting.cashToPosting(
            Money.of(BigDecimal("500"), currency),
            CashDirection.IN,
            accounts,
            "",
        )
        assertEquals("CASH", posting.documentType)
        assertNull(posting.partyEffect)
        assertNull(posting.partyId)
        assertTrue(posting.stockMovements.isEmpty())
        assertEquals(0, posting.totals.grandTotal.amount.compareTo(BigDecimal("500")))
        val lines = posting.journalLines
        assertEquals(2, lines.size)
        assertEquals("cash", lines[0].accountId.value)
        assertEquals(0, lines[0].debit.amount.compareTo(BigDecimal("500")))
        assertTrue(lines[0].credit.isZero)
        assertEquals("sales", lines[1].accountId.value)
        assertTrue(lines[1].debit.isZero)
        assertEquals(0, lines[1].credit.amount.compareTo(BigDecimal("500")))
    }

    @Test fun cashOutDebitsCounterpartCreditsCash() {
        val posting = CashPosting.cashToPosting(
            Money.of(BigDecimal("250"), currency),
            CashDirection.OUT,
            accounts,
            "",
        )
        assertEquals("CASH", posting.documentType)
        val lines = posting.journalLines
        assertEquals("cash", lines[0].accountId.value)
        assertTrue(lines[0].debit.isZero)
        assertEquals(0, lines[0].credit.amount.compareTo(BigDecimal("250")))
        assertEquals("sales", lines[1].accountId.value)
        assertEquals(0, lines[1].debit.amount.compareTo(BigDecimal("250")))
        assertTrue(lines[1].credit.isZero)
    }
}
