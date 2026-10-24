package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import java.math.BigDecimal

/**
 * Phase 22.3 — one row of the party ledger: a single posting
 * effect, stored as a "party_ledger" record. That table IS
 * sync-whitelisted AND INSERT_ONLY in the sync queue, so
 * ledger rows are append-only by platform rule — a
 * correction is a reversing entry, never an edit (the §181
 * rule, applied to money), exactly like the 20.2
 * "account_transactions" rows.
 *
 * One row per posting effect (a DocumentPosting carries at
 * most one PartyLedgerEffect), so the row id IS the journal
 * entry id ("je-<transactionId>"): re-posting the same
 * document overwrites the same row instead of duplicating
 * it, and the 21.x backfill is idempotent by transaction
 * id for the same reason — a second backfill run writes
 * the same row ids. Amount is SIGNED: positive increases
 * the balance in [direction] (PartyLedgerEffect's
 * contract). Currency is stored on the row (unlike the
 * 20.1/20.2 records, where currency is caller context)
 * because ledger balances are computed per party per
 * currency — summing across currencies would be wrong.
 * Direction is stored as text; balance functions count
 * only exact "RECEIVABLE"/"PAYABLE" rows and ignore
 * anything else rather than misattributing it.
 */
data class PartyLedgerRecord(
    val id: String,
    /** The journal entry this row belongs to ("je-<transactionId>"). */
    val entryId: String = "",
    val partyId: String = "",
    val direction: String = "RECEIVABLE",
    /** Signed amount; see the class doc. */
    val amount: BigDecimal = BigDecimal.ZERO,
    /** CurrencyCode value (e.g. "PKR"). */
    val currency: String = "",
    /** The posting's transaction id, the idempotency key. */
    val transactionId: String = "",
    val documentType: String = "",
    val documentNumber: String = "",
    /** ISO date text as entered (yyyy-MM-dd); no calendar math in this layer. */
    val date: String = "",
    val description: String = "",
    /** ISO instant the row was recorded. */
    val createdAt: String = "",
) {
    companion object {
        /**
         * One ledger row for a posting's effect, or null when
         * the posting carries no effect (nothing to record).
         * The row id is the journal entry id, so re-posts
         * and backfill re-runs overwrite instead of
         * duplicating.
         */
        fun fromPosting(
            posting: DocumentPosting,
            entry: JournalEntryRecord,
        ): PartyLedgerRecord? {
            val effect = posting.partyEffect ?: return null
            return PartyLedgerRecord(
                id = entry.id,
                entryId = entry.id,
                partyId = effect.partyId.value,
                direction = effect.direction.name,
                amount = effect.amount.amount,
                currency = effect.amount.currency.value,
                transactionId = posting.transactionId.value,
                documentType = posting.documentType,
                documentNumber = posting.documentNumber,
                date = entry.date,
                description = entry.description,
                createdAt = entry.createdAt,
            )
        }
    }
}
