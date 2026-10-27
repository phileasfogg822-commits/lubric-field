package com.lubrcapp.lubrcapp.domain.business

import java.time.format.DateTimeParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {

    private val due = "2026-10-10"
    private val rule = ReminderRule(id = "r1", partyId = "p1")

    @Test
    fun fixedCadencePointsAreReminderDates() {
        for (date in listOf("2026-10-07", "2026-10-10", "2026-10-13", "2026-10-17")) {
            assertTrue(date, ReminderSchedule.isReminderDate(due, date))
        }
    }

    @Test
    fun weeklyTailContinuesEverySevenDays() {
        for (date in listOf("2026-10-24", "2026-10-31", "2026-11-07")) {
            assertTrue(date, ReminderSchedule.isReminderDate(due, date))
        }
    }

    @Test
    fun nonCadenceDatesAreNotReminderDates() {
        for (date in listOf("2026-10-06", "2026-10-09", "2026-10-11", "2026-10-20", "2026-10-25")) {
            assertFalse(date, ReminderSchedule.isReminderDate(due, date))
        }
    }

    @Test
    fun nextReminderAfterFindsEachFixedPoint() {
        assertEquals("2026-10-07", ReminderSchedule.nextReminderAfter(rule, due, "2026-10-06"))
        assertEquals("2026-10-10", ReminderSchedule.nextReminderAfter(rule, due, "2026-10-07"))
        assertEquals("2026-10-13", ReminderSchedule.nextReminderAfter(rule, due, "2026-10-10"))
        assertEquals("2026-10-17", ReminderSchedule.nextReminderAfter(rule, due, "2026-10-13"))
    }

    @Test
    fun nextReminderAfterJumpsToTheWeeklyTail() {
        assertEquals("2026-10-24", ReminderSchedule.nextReminderAfter(rule, due, "2026-10-17"))
        assertEquals("2026-10-24", ReminderSchedule.nextReminderAfter(rule, due, "2026-10-20"))
        assertEquals("2027-01-09", ReminderSchedule.nextReminderAfter(rule, due, "2027-01-03"))
    }

    @Test
    fun disabledRuleYieldsNoReminder() {
        val off = ReminderRule(id = "r2", partyId = "p1", enabled = false)
        assertNull(ReminderSchedule.nextReminderAfter(off, due, "2026-10-01"))
    }

    @Test
    fun overdueMeansStrictlyPastDue() {
        assertTrue(ReminderSchedule.isOverdue(due, "2026-10-11"))
        assertFalse(ReminderSchedule.isOverdue(due, "2026-10-10"))
        assertFalse(ReminderSchedule.isOverdue(due, "2026-10-09"))
    }

    @Test
    fun malformedDatesThrowInsteadOfSilentlyComputing() {
        assertThrows(DateTimeParseException::class.java) {
            ReminderSchedule.isReminderDate("10-10-2026", "2026-10-07")
        }
    }
}
