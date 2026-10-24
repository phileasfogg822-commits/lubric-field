package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.JournalEntryId
import com.lubrcapp.lubrcapp.domain.common.JournalLineId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 20.2 — the posting engine: the single gate through
 * which a 17.4 [DocumentPosting] becomes a journal entry.
 *
 * The engine does two things and nothing else:
 * 1. Runs [PostingValidator] (totals reconciliation, balance,
 *    one-sided lines, party consistency, §18 credit block).
 * 2. On zero violations, builds the balanced domain
 *    [JournalEntry] from the posting's journal lines.
 *
 * The entry id is DERIVED from the document's transaction id
 * ("je-<transactionId>"), never generated fresh: re-posting
 * the same document overwrites the same entry (and, in the
 * feature layer, the same transaction rows), so a retry after
 * a crash cannot double-post. Callers that post distinct
 * documents use distinct transaction ids.
 *
 * Tenant, business, date and currency context are the
 * caller's — the posting contract deliberately carries none
 * of them (the Phase 19.1 `toPostingMovement(currency)`
 * pattern). The engine writes nothing: persistence is the
 * feature layer's job (journal record + `account_transactions`
 * rows, saved by the AccountsViewModel's postDocument).
 *
 * The engine posts no stock movements and no party effects
 * itself — those are the Phase 19 ledger's and Phase 22
 * ledger's jobs from the same [DocumentPosting].
 */
sealed class PostingResult {
    /** The balanced entry the posting produced. */
    data class Posted(val entry: JournalEntry) : PostingResult()

    /** The posting was rejected; nothing was produced. */
    data class Rejected(val violations: List<PostingViolation>) : PostingResult()
}

object PostingEngine {

    fun post(
        posting: DocumentPosting,
        tenantId: TenantId,
        businessId: BusinessId,
        date: LocalDate,
        description: String = "${posting.documentType} ${posting.documentNumber}",
        now: Instant,
        deviceId: DeviceId,
        creditCheck: CreditCheck? = null,
    ): PostingResult {
        val violations = PostingValidator.validate(posting, creditCheck)
        if (violations.isNotEmpty()) return PostingResult.Rejected(violations)

        val entryId = JournalEntryId("je-${posting.transactionId.value}")
        return PostingResult.Posted(
            JournalEntry(
                id = entryId,
                tenantId = tenantId,
                businessId = businessId,
                date = date,
                description = description,
                referenceNumber = posting.documentNumber,
                lines = posting.journalLines.mapIndexed { index, line ->
                    JournalLine(
                        id = JournalLineId("${entryId.value}-$index"),
                        entryId = entryId,
                        accountId = line.accountId,
                        debit = line.debit,
                        credit = line.credit,
                        memo = null,
                    )
                },
                metadata = EntityMetadata.newLocal(now, deviceId),
            ),
        )
    }
}
