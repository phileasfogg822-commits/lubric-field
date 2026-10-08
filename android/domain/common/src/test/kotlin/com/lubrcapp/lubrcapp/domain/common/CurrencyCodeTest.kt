package com.lubrcapp.lubrcapp.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurrencyCodeTest {
    @Test
    fun factoryNormalizesCaseAndWhitespace() {
        assertEquals(CurrencyCode("PKR"), CurrencyCode.of(" pkr "))
    }

    @Test
    fun invalidCodesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("US") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("usd") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("US12") }
    }
}
