package com.lubrcapp.lubrcapp.domain.business

import java.time.LocalDate

/**
 * Phase 29.2 — reminder schedule rules (§103).
 * Pure date math over ISO `yyyy-MM-dd` text: the
 * automatic-reminder cadence is 3 days before the
 * due date, the due date itself, 3 days after,
 * 7 days after, then every 7 days after that.
 * A rule can be disabled per customer (§103) —
 * a disabled rule yields no reminder date at all.
 *
 * Dates are parsed with [LocalDate], so a
 * non-ISO input throws rather than silently
 * computing from a malformed string.
 */
data class ReminderRule(
    val id: String,
    val partyId: String,
    val enabled: Boolean = true,
)

object ReminderSchedule {
    private val FIXED_OFFSETS = listOf(-3L, 0L, 3L)

    fun isReminderDate(dueDate: String, candidate: String): Boolean {
        val due = LocalDate.parse(dueDate)
        val diff = LocalDate.parse(candidate).toEpochDay() - due.toEpochDay()
        if (diff in FIXED_OFFSETS) return true
        if (diff < 7L) return false
        return (diff - 7L) % 7L == 0L
    }

    /** The first cadence date strictly after [after], or null when disabled. */
    fun nextReminderAfter(rule: ReminderRule, dueDate: String, after: String): String? {
        if (!rule.enabled) return null
        val due = LocalDate.parse(dueDate)
        val afterDay = LocalDate.parse(after)
        for (offset in FIXED_OFFSETS) {
            val candidate = due.plusDays(offset)
            if (candidate > afterDay) return candidate.toString()
        }
        var offset = 7L
        while (due.plusDays(offset) <= afterDay) offset += 7L
        return due.plusDays(offset).toString()
    }

    /** Past the due date with no payment recorded — a display fact, not a posting. */
    fun isOverdue(dueDate: String, today: String): Boolean =
        LocalDate.parse(today) > LocalDate.parse(dueDate)
}
