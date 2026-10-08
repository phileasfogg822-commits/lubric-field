package com.lubrcapp.lubrcapp.core.store.db

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainTypeConvertersTest {
    private val converters = DomainTypeConverters()

    @Test
    fun exactDecimalRoundTripsWithoutDoubleConversion() {
        val value = BigDecimal("1234567890.123456789")
        assertEquals(value, converters.stringToBigDecimal(converters.bigDecimalToString(value)))
    }

    @Test
    fun timeAndDomainTypesRoundTrip() {
        val instant = Instant.parse("2026-10-08T12:34:56.789Z")
        val date = LocalDate.parse("2026-10-08")
        val zone = ZoneId.of("Asia/Karachi")
        val currency = CurrencyCode.of("PKR")

        assertEquals(instant, converters.epochMillisToInstant(converters.instantToEpochMillis(instant)))
        assertEquals(date, converters.isoToLocalDate(converters.localDateToIso(date)))
        assertEquals(zone, converters.stringToZoneId(converters.zoneIdToString(zone)))
        assertEquals(currency, converters.stringToCurrencyCode(converters.currencyCodeToString(currency)))
        assertEquals(
            SyncStatus.CONFLICT,
            converters.stringToSyncStatus(converters.syncStatusToString(SyncStatus.CONFLICT)),
        )
    }
}
