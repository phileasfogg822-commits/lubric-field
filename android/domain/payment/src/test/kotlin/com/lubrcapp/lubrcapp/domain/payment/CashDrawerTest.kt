package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CashDrawerTest {

    private val pkr = CurrencyCode("PKR")
    private val usd = CurrencyCode("USD")

    private fun session() = CashDrawer.openSession("drawer-1", Money.of("1000", pkr))

    private fun move(amount: String, direction: DrawerMovementDirection, note: String = "") =
        DrawerMovement(Money.of(amount, pkr), direction, note)

    @Test
    fun openSessionStartsWithOpeningCash() {
        val opened = session()
        assertEquals(Money.of("1000", pkr), opened.openingCash)
        assertEquals(0, opened.movements.size)
        assertEquals(Money.of("1000", pkr), CashDrawer.expectedCash(opened))
    }

    @Test
    fun expectedCashAddsInAndSubtractsOut() {
        val updated = session()
            .let { CashDrawer.recordMovement(it, move("500", DrawerMovementDirection.IN, "sale")) }
            .let { CashDrawer.recordMovement(it, move("200", DrawerMovementDirection.OUT, "expense")) }
        assertEquals(Money.of("1300", pkr), CashDrawer.expectedCash(updated))
    }

    @Test
    fun closeExactGivesZeroDifference() {
        val closed = CashDrawer.closeSession(session(), Money.of("1000", pkr))
        assertEquals(Money.of("1000", pkr), closed.expected)
        assertEquals(Money.of("1000", pkr), closed.actual)
        assertEquals(Money.zero(pkr), closed.difference)
    }

    @Test
    fun closeOverGivesPositiveDifference() {
        val updated = CashDrawer.recordMovement(
            session(),
            move("500", DrawerMovementDirection.IN),
        )
        val closed = CashDrawer.closeSession(updated, Money.of("1600", pkr))
        assertEquals(Money.of("1500", pkr), closed.expected)
        assertEquals(Money.of("100", pkr), closed.difference)
    }

    @Test
    fun closeShortGivesNegativeDifference() {
        val updated = CashDrawer.recordMovement(
            session(),
            move("200", DrawerMovementDirection.OUT),
        )
        val closed = CashDrawer.closeSession(updated, Money.of("700", pkr))
        assertEquals(Money.of("800", pkr), closed.expected)
        assertEquals(Money.of("-100", pkr), closed.difference)
    }

    @Test
    fun mixedCurrencyMovementThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            CashDrawer.recordMovement(
                session(),
                DrawerMovement(Money.of("100", usd), DrawerMovementDirection.IN),
            ).let { CashDrawer.expectedCash(it) }
        }
    }

    @Test
    fun closeDoesNotMutateSession() {
        val opened = session()
        CashDrawer.closeSession(opened, Money.of("1000", pkr))
        assertEquals(0, opened.movements.size)
    }
}
