package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal

/**
 * Phase 20.2 — one row of the syncing transaction truth:
 * a single journal line, stored as an "account_transactions"
 * record. That table IS sync-whitelisted AND INSERT_ONLY in
 * the sync queue, so transaction rows are append-only by
 * platform rule — a correction is a reversing entry, never
 * an edit (the §181 rule, applied to money).
 *
 * This is the mirror of the local "journal_entries" record
 * (Phase 20.1): the journal entry is the local auditable
 * record with lines embedded; these rows are the per-line
 * truth that syncs. The pair is written together by the
 * AccountsViewModel's postDocument, and both sides derive
 * stable ids from the entry id ("<entryId>-<index>"), so a
 * re-post overwrites the same rows instead of duplicating
 * them.
 *
 * Amounts are plain BigDecimals in storage; currency is the
 * caller's context, as in 20.1.
 */
data class AccountTransactionRecord(
    val id: String,
    /** The journal entry this row belongs to. */
    val entryId: String,
    val accountId: String,
    val debit: BigDecimal = BigDecimal.ZERO,
    val credit: BigDecimal = BigDecimal.ZERO,
    /** ISO date text as entered (yyyy-MM-dd); no calendar math in this layer. */
    val date: String = "",
    val description: String = "",
    /** The document number this row posts for (e.g. "INV-2026-00001"), when one did. */
    val referenceNumber: String? = null,
    /** ISO instant the row was recorded. */
    val createdAt: String = "",
) {
    companion object {
        /**
         * One transaction row per journal line, with stable
         * ids ("<entryId>-<index>") so re-posting the same
         * entry overwrites the same rows.
         */
        fun fromEntry(entry: JournalEntryRecord): List<AccountTransactionRecord> =
            entry.lines.mapIndexed { index, line ->
                AccountTransactionRecord(
                    id = "${entry.id}-$index",
                    entryId = entry.id,
                    accountId = line.accountId,
                    debit = line.debit,
                    credit = line.credit,
                    date = entry.date,
                    description = entry.description,
                    referenceNumber = entry.referenceNumber,
                    createdAt = entry.createdAt,
                )
            }
    }
}
