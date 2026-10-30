package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerRecord
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryMappingTest {

    private fun row(
        id: String,
        partyId: String = "p1",
        direction: String = "RECEIVABLE",
        amount: String = "0",
        currency: String = "PKR",
    ) = PartyLedgerRecord(
        id = id,
        partyId = partyId,
        direction = direction,
        amount = BigDecimal(amount),
        currency = currency,
    )

    @Test
    fun sumsSignedAmountsPerDirectionInChosenCurrency() {
        val summary = summarizeHomeLedger(
            listOf(
                row("r1", amount = "4400"),
                row("r2", amount = "-220"),
                row("r3", direction = "PAYABLE", amount = "24400"),
                row("r4", direction = "PAYABLE", amount = "-10000"),
            ),
        )
        assertEquals("PKR", summary.currency)
        assertEquals(BigDecimal("4180"), summary.receivable)
        assertEquals(BigDecimal("14400"), summary.payable)
    }

    @Test
    fun rowsInOtherCurrenciesAreIgnored() {
        val summary = summarizeHomeLedger(
            listOf(
                row("r1", amount = "1000", currency = "PKR"),
                row("r2", amount = "9999", currency = "USD"),
                row("r3", direction = "PAYABLE", amount = "500", currency = "USD"),
            ),
        )
        assertEquals("PKR", summary.currency)
        assertEquals(BigDecimal("1000"), summary.receivable)
        assertEquals(BigDecimal("0"), summary.payable)
    }

    @Test
    fun unknownDirectionsAreIgnored() {
        val summary = summarizeHomeLedger(
            listOf(
                row("r1", amount = "1000"),
                row("r2", direction = "OTHER", amount = "700"),
            ),
        )
        assertEquals(BigDecimal("1000"), summary.receivable)
        assertEquals(BigDecimal("0"), summary.payable)
    }

    @Test
    fun emptyRowsYieldZeroXxx() {
        val summary = summarizeHomeLedger(emptyList())
        assertEquals("XXX", summary.currency)
        assertEquals(BigDecimal.ZERO, summary.receivable)
        assertEquals(BigDecimal.ZERO, summary.payable)
    }

    @Test
    fun blankCurrencyRowsDoNotChooseCurrency() {
        val summary = summarizeHomeLedger(
            listOf(
                row("r1", amount = "1000", currency = ""),
                row("r2", amount = "2000", currency = "PKR"),
            ),
        )
        assertEquals("PKR", summary.currency)
        assertEquals(BigDecimal("2000"), summary.receivable)
    }
}
