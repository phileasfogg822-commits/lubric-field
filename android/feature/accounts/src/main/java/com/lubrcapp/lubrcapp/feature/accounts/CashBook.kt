package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal

/**
 * Phase 31.3 — pure cash-book reads over stored journal entries.
 * Dates are ISO text, so lexicographic order is chronological
 * (the JournalEntryRecord contract). Balance is debit minus
 * credit on that account's lines only; other accounts' lines
 * in the same entries never leak in.
 */
object CashBook {

    fun entriesForAccount(
        entries: List<JournalEntryRecord>,
        accountId: String,
    ): List<JournalEntryRecord> =
        entries.filter { entry -> entry.lines.any { it.accountId == accountId } }
            .sortedBy { it.date }

    fun balance(
        entries: List<JournalEntryRecord>,
        accountId: String,
    ): BigDecimal =
        entries.flatMap { it.lines }
            .filter { it.accountId == accountId }
            .fold(BigDecimal.ZERO) { acc, line -> acc + line.debit - line.credit }
}
