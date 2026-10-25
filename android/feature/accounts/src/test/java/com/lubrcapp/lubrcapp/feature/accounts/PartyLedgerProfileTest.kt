package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyLedgerProfileTest {

    private val pkr = CurrencyCode.of("PKR")
    private val usd = CurrencyCode.of("USD")

    private fun row(
        id: String,
        amount: String,
        documentType: String = "INVOICE",
        partyId: String = "party-1",
        currency: String = "PKR",
        direction: String = "RECEIVABLE",
        date: String = "2026-10-01",
    ): PartyLedgerRecord = PartyLedgerRecord(
        id = id,
        partyId = partyId,
        direction = direction,
        amount = BigDecimal(amount),
        currency = currency,
        documentType = documentType,
        documentNumber = id,
        date = date,
    )

    @Test
    fun `profile builds balance outstanding statement and payments`() {
        val rows = listOf(
            row("je-1", "100000", "INVOICE", date = "2026-10-01"),
            row("je-2", "-40000", "PAYMENT", date = "2026-10-05"),
            row("je-3", "-10000", "SALES_RETURN", date = "2026-10-10"),
        )
        val profile = PartyLedgerProfile.build(rows, "party-1", pkr)
        assertEquals(Money.of("50000", pkr), profile.balance)
        assertEquals(Money.of("50000", pkr), profile.outstanding)
        assertEquals(3, profile.transactions.size)
        assertEquals(3, profile.statement.size)
        assertEquals(Money.of("100000", pkr), profile.statement[0].runningBalance)
        assertEquals(Money.of("60000", pkr), profile.statement[1].runningBalance)
        assertEquals(Money.of("50000", pkr), profile.statement[2].runningBalance)
        assertEquals(listOf("je-2"), profile.payments.map { it.id })
    }

    @Test
    fun `credit balance stays negative but outstanding is zero`() {
        val rows = listOf(
            row("je-1", "-100000", "SALES_RETURN", date = "2026-10-01"),
            row("je-2", "30000", "REFUND", date = "2026-10-05"),
        )
        val profile = PartyLedgerProfile.build(rows, "party-1", pkr)
        assertEquals(Money.of("-70000", pkr), profile.balance)
        assertEquals(Money.of("0", pkr), profile.outstanding)
        assertEquals(Money.of("-100000", pkr), profile.statement[0].runningBalance)
        assertEquals(Money.of("-70000", pkr), profile.statement[1].runningBalance)
        assertEquals(listOf("je-2"), profile.payments.map { it.id })
    }

    @Test
    fun `other party currency direction rows are ignored`() {
        val rows = listOf(
            row("je-1", "100000", "INVOICE"),
            row("je-2", "99999", "INVOICE", partyId = "party-2"),
            row("je-3", "88888", "INVOICE", currency = "USD"),
            row("je-4", "77777", "INVOICE", direction = "PAYABLE"),
            row("je-5", "66666", "INVOICE", direction = "UNKNOWN"),
        )
        val profile = PartyLedgerProfile.build(rows, "party-1", pkr)
        assertEquals(Money.of("100000", pkr), profile.balance)
        assertEquals(listOf("je-1"), profile.transactions.map { it.id })
        // The USD rows belong to a USD profile, not this one.
        assertEquals(Money.of("88888", usd), PartyLedgerProfile.build(rows, "party-1", usd).balance)
    }

    @Test
    fun `empty rows give zero balance and empty views`() {
        val profile = PartyLedgerProfile.build(emptyList(), "party-1", pkr)
        assertEquals(Money.of("0", pkr), profile.balance)
        assertEquals(Money.of("0", pkr), profile.outstanding)
        assertTrue(profile.transactions.isEmpty())
        assertTrue(profile.statement.isEmpty())
        assertTrue(profile.payments.isEmpty())
    }

    @Test
    fun `payments list holds payments and refunds only in order`() {
        val rows = listOf(
            row("je-1", "100000", "INVOICE", date = "2026-10-01"),
            row("je-2", "-40000", "PAYMENT", date = "2026-10-02"),
            row("je-3", "-10000", "SALES_RETURN", date = "2026-10-03"),
            row("je-4", "5000", "REFUND", date = "2026-10-04"),
            row("je-5", "-2000", "CREDIT_NOTE", date = "2026-10-05"),
        )
        val profile = PartyLedgerProfile.build(rows, "party-1", pkr)
        assertEquals(listOf("je-2", "je-4"), profile.payments.map { it.id })
    }
}
