package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyStatementTest {

    private val pkr = CurrencyCode("PKR")

    private fun row(
        id: String,
        partyId: String = "p1",
        direction: String = "RECEIVABLE",
        amount: String,
        currency: String = "PKR",
        date: String,
    ): PartyLedgerRecord = PartyLedgerRecord(
        id = id,
        partyId = partyId,
        direction = direction,
        amount = BigDecimal(amount),
        currency = currency,
        date = date,
    )

    @Test
    fun openingMovementsAndClosingBalance() {
        val statement = partyStatement(
            rows = listOf(
                row("r-before", amount = "100000", date = "2026-09-30"),
                row("r-in-1", amount = "50000", date = "2026-10-05"),
                row("r-in-2", amount = "-20000", date = "2026-10-20"),
                row("r-after", amount = "99999", date = "2026-11-01"),
            ),
            partyId = "p1",
            direction = "RECEIVABLE",
            currency = pkr,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(Money.of(BigDecimal("100000"), pkr), statement.openingBalance)
        assertEquals(listOf("r-in-1", "r-in-2"), statement.movements.map { it.id })
        assertEquals(Money.of(BigDecimal("130000"), pkr), statement.closingBalance)
    }

    @Test
    fun otherPartyDirectionAndCurrencyAreExcluded() {
        val statement = partyStatement(
            rows = listOf(
                row("r-other-party", partyId = "p2", amount = "100000", date = "2026-10-05"),
                row("r-payable", direction = "PAYABLE", amount = "100000", date = "2026-10-05"),
                row("r-usd", currency = "USD", amount = "100000", date = "2026-10-05"),
            ),
            partyId = "p1",
            direction = "RECEIVABLE",
            currency = pkr,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertTrue(statement.movements.isEmpty())
        assertEquals(Money.of(BigDecimal.ZERO, pkr), statement.openingBalance)
        assertEquals(Money.of(BigDecimal.ZERO, pkr), statement.closingBalance)
    }

    @Test
    fun invertedRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            partyStatement(
                rows = emptyList(),
                partyId = "p1",
                direction = "RECEIVABLE",
                currency = pkr,
                fromDate = "2026-10-31",
                toDate = "2026-10-01",
            )
        }
    }

    @Test
    fun emptyRowsProduceZeroBalances() {
        val statement = partyStatement(
            rows = emptyList(),
            partyId = "p1",
            direction = "RECEIVABLE",
            currency = pkr,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertTrue(statement.movements.isEmpty())
        assertEquals(Money.of(BigDecimal.ZERO, pkr), statement.openingBalance)
        assertEquals(Money.of(BigDecimal.ZERO, pkr), statement.closingBalance)
    }
}
