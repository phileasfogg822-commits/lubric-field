package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class PartyLiveBalanceTest {

    @Test
    fun pureReceivable() {
        assertEquals(
            PartyLiveBalance(BigDecimal("100"), BigDecimal.ZERO),
            combineLiveBalance(BigDecimal("100"), BigDecimal.ZERO),
        )
    }

    @Test
    fun purePayable() {
        assertEquals(
            PartyLiveBalance(BigDecimal.ZERO, BigDecimal("250")),
            combineLiveBalance(BigDecimal.ZERO, BigDecimal("250")),
        )
    }

    @Test
    fun receivableCreditBalanceBecomesPayable() {
        assertEquals(
            PartyLiveBalance(BigDecimal.ZERO, BigDecimal("50")),
            combineLiveBalance(BigDecimal("-50"), BigDecimal.ZERO),
        )
    }

    @Test
    fun supplierAdvanceBecomesReceivable() {
        assertEquals(
            PartyLiveBalance(BigDecimal("30"), BigDecimal.ZERO),
            combineLiveBalance(BigDecimal.ZERO, BigDecimal("-30")),
        )
    }

    @Test
    fun bothDirectionsStaySeparate() {
        assertEquals(
            PartyLiveBalance(BigDecimal("100"), BigDecimal("250")),
            combineLiveBalance(BigDecimal("100"), BigDecimal("250")),
        )
    }

    @Test
    fun bothCreditsFoldIn() {
        assertEquals(
            PartyLiveBalance(BigDecimal("30"), BigDecimal("50")),
            combineLiveBalance(BigDecimal("-50"), BigDecimal("-30")),
        )
    }

    @Test
    fun zeroStaysZero() {
        assertEquals(
            PartyLiveBalance(BigDecimal.ZERO, BigDecimal.ZERO),
            combineLiveBalance(BigDecimal.ZERO, BigDecimal.ZERO),
        )
    }
}
