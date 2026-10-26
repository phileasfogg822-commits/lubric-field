package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShiftTest {

    private val pkr = CurrencyCode("PKR")
    private val usd = CurrencyCode("USD")

    private fun shift() = ShiftOps.openShift("shift-1", Money.of("1000", pkr))

    private fun entry(kind: ShiftEntryKind, amount: String) =
        ShiftEntry(kind, Money.of(amount, pkr))

    @Test
    fun openShiftStartsWithZeroSummary() {
        val summary = ShiftOps.summary(shift())
        assertEquals(Money.zero(pkr), summary.sales)
        assertEquals(Money.zero(pkr), summary.returns)
        assertEquals(Money.of("1000", pkr), summary.cashExpected)
        assertEquals(Money.zero(pkr), summary.card)
        assertEquals(Money.zero(pkr), summary.expenses)
        assertEquals(null, summary.cashActual)
        assertEquals(null, summary.cashDifference)
    }

    @Test
    fun summaryTotalsEachCategory() {
        val updated = shift()
            .let { ShiftOps.recordEntry(it, entry(ShiftEntryKind.SALES, "2000")) }
            .let { ShiftOps.recordEntry(it, entry(ShiftEntryKind.SALES, "500")) }
            .let { ShiftOps.recordEntry(it, entry(ShiftEntryKind.RETURNS, "300")) }
            .let { ShiftOps.recordEntry(it, entry(ShiftEntryKind.CARD, "1500")) }
            .let { ShiftOps.recordEntry(it, entry(ShiftEntryKind.EXPENSES, "200")) }
        val summary = ShiftOps.summary(updated)
        assertEquals(Money.of("2500", pkr), summary.sales)
        assertEquals(Money.of("300", pkr), summary.returns)
        assertEquals(Money.of("1500", pkr), summary.card)
        assertEquals(Money.of("200", pkr), summary.expenses)
    }

    @Test
    fun drawerMovementsFeedCashExpected() {
        val updated = ShiftOps.recordDrawerMovement(
            shift(),
            DrawerMovement(Money.of("700", pkr), DrawerMovementDirection.IN, "sale"),
        )
        assertEquals(Money.of("1700", pkr), ShiftOps.summary(updated).cashExpected)
    }

    @Test
    fun closeShiftReportsCashDifference() {
        val updated = ShiftOps.recordDrawerMovement(
            shift(),
            DrawerMovement(Money.of("500", pkr), DrawerMovementDirection.IN),
        )
        val closed = ShiftOps.closeShift(updated, Money.of("1400", pkr))
        assertEquals(Money.of("1500", pkr), closed.cashExpected)
        assertEquals(Money.of("1400", pkr), closed.cashActual)
        assertEquals(Money.of("-100", pkr), closed.cashDifference)
    }

    @Test
    fun mixedCurrencyEntryThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            ShiftOps.summary(
                ShiftOps.recordEntry(
                    shift(),
                    ShiftEntry(ShiftEntryKind.SALES, Money.of("100", usd)),
                ),
            )
        }
    }

    @Test
    fun closeDoesNotMutateSession() {
        val opened = shift()
        ShiftOps.closeShift(opened, Money.of("1000", pkr))
        assertEquals(0, opened.entries.size)
        assertEquals(0, opened.drawer.movements.size)
    }
}
