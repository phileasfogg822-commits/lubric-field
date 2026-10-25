package com.lubrcapp.lubrcapp.feature.accounts

/**
 * Phase 24.6 — the day book report: the chronological
 * journal listing over an inclusive ISO date range. Every
 * entry in the range, sorted by date then entry id. No
 * aggregation and no currency question — entries carry
 * their lines exactly as posted. An inverted range is a
 * caller error.
 */
data class DayBook(
    val fromDate: String,
    val toDate: String,
    val entries: List<JournalEntryRecord>,
)

fun dayBook(
    entries: List<JournalEntryRecord>,
    fromDate: String,
    toDate: String,
): DayBook {
    require(fromDate <= toDate) { "fromDate must not be after toDate" }
    val period = entries
        .filter { it.date in fromDate..toDate }
        .sortedWith(compareBy({ it.date }, { it.id }))
    return DayBook(fromDate = fromDate, toDate = toDate, entries = period)
}
