package com.lubrcapp.lubrcapp.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateFieldDatesTest {

    @Test
    fun isoRoundTrip() {
        val millis = DatePickerDates.isoDateToUtcMillis("2026-10-18")
        assertEquals("2026-10-18", millis?.let { DatePickerDates.utcMillisToIsoDate(it) })
    }

    @Test
    fun leapDayRoundTrip() {
        val millis = DatePickerDates.isoDateToUtcMillis("2024-02-29")
        assertEquals("2024-02-29", millis?.let { DatePickerDates.utcMillisToIsoDate(it) })
    }

    @Test
    fun epochIsZero() {
        assertEquals(0L, DatePickerDates.isoDateToUtcMillis("1970-01-01"))
        assertEquals("1970-01-01", DatePickerDates.utcMillisToIsoDate(0L))
    }

    @Test
    fun invalidInputReturnsNull() {
        assertNull(DatePickerDates.isoDateToUtcMillis(""))
        assertNull(DatePickerDates.isoDateToUtcMillis("not-a-date"))
        assertNull(DatePickerDates.isoDateToUtcMillis("2026-13-01"))
    }
}
