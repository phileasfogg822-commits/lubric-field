package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.JournalEntry
import com.lubrcapp.lubrcapp.domain.accounting.JournalLine
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.JournalEntryId
import com.lubrcapp.lubrcapp.domain.common.JournalLineId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 20.1 — the typed journal entry: one balanced double-entry
 * record, stored as a "journal_entries" record with its lines
 * EMBEDDED (the Phase 19.5 atomic-record precedent: one record
 * keeps the entry and its lines consistent under status changes
 * and partial writes).
 *
 * That table is NOT sync-whitelisted → local-only, the
 * header-local / movements-sync split from Phase 19.5: the
 * journal is the local auditable record, while the whitelisted
 * `account_transactions` truth syncs (written by the 20.2
 * posting engine alongside it).
 *
 * Amounts are plain BigDecimals in storage; currency is the
 * caller's context (currency wiring is still shell work), so
 * [toDomainEntry] takes it as a parameter — the Phase 19.1
 * `toPostingMovement(currency)` pattern. A journal line carries
 * BOTH a debit and a credit (the domain shape); a one-sided
 * line stores ZERO on the other side.
 *
 * Balance is a property, not a gate, at this layer: [isBalanced]
 * reports it, and the 20.2 posting engine enforces it before
 * anything posts. An entry with no lines sums to zero on both
 * sides and reports balanced; the engine requires real lines.
 */
data class JournalLineRecord(
    val accountId: String,
    val debit: BigDecimal = BigDecimal.ZERO,
    val credit: BigDecimal = BigDecimal.ZERO,
    val memo: String? = null,
)

data class JournalEntryRecord(
    val id: String,
    /** ISO date text as entered (yyyy-MM-dd); no calendar math in this layer. */
    val date: String = "",
    val description: String = "",
    /** The document number this entry posts for (e.g. "INV-2026-00001"), when one did. */
    val referenceNumber: String? = null,
    val lines: List<JournalLineRecord> = emptyList(),
    /** ISO instant the entry was recorded. */
    val createdAt: String = "",
) {
    /** Sum of line debits; BigDecimal comparison, never Double. */
    val totalDebits: BigDecimal
        get() = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.debit }

    /** Sum of line credits; BigDecimal comparison, never Double. */
    val totalCredits: BigDecimal
        get() = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.credit }

    /** True when debits equal credits. The 20.2 engine enforces this before posting. */
    val isBalanced: Boolean
        get() = totalDebits.compareTo(totalCredits) == 0

    /**
     * The domain/accounting form of this entry. [currency] and
     * the scope ids are the caller's because a stored journal
     * amount only has meaning in a business's currency. The
     * stored date must parse as ISO yyyy-MM-dd; writers
     * validate it before saving (the ViewModel rejects
     * unparseable dates). Line ids are deterministic
     * ("<entryId>-<index>") so re-derivation is stable.
     */
    fun toDomainEntry(
        tenantId: TenantId,
        businessId: BusinessId,
        currency: CurrencyCode,
        deviceId: DeviceId,
        now: Instant,
    ): JournalEntry = JournalEntry(
        id = JournalEntryId(id),
        tenantId = tenantId,
        businessId = businessId,
        date = LocalDate.parse(date),
        description = description,
        referenceNumber = referenceNumber,
        lines = lines.mapIndexed { index, line ->
            JournalLine(
                id = JournalLineId("$id-$index"),
                entryId = JournalEntryId(id),
                accountId = AccountId(line.accountId),
                debit = Money.of(line.debit, currency),
                credit = Money.of(line.credit, currency),
                memo = line.memo,
            )
        },
        metadata = EntityMetadata.newLocal(now, deviceId),
    )

    companion object {
        /**
         * Build an entry with a fresh id and timestamp; the
         * caller supplies the business content. Balance is NOT
         * checked here — the 20.2 engine is the gate.
         */
        fun of(
            date: String,
            description: String,
            lines: List<JournalLineRecord>,
            referenceNumber: String? = null,
        ): JournalEntryRecord = JournalEntryRecord(
            id = UUID.randomUUID().toString(),
            date = date,
            description = description,
            referenceNumber = referenceNumber,
            lines = lines,
            createdAt = Instant.now().toString(),
        )
    }
}
