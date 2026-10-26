package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 28.6 — archive rules (§209). The spec
 * says prefer archive instead of delete for
 * Products, Customers, Categories and Brands,
 * and that historical transactions must still
 * display archived records.
 *
 * Archive is therefore a flag and a visibility
 * rule, NOT delete semantics: the repository's
 * soft-delete is a different operation and is
 * never reused here. An archived record vanishes
 * from pickers ([visible]) but stays in history
 * ([history]) — the same list, unfiltered. Both
 * transitions are idempotent: archiving an
 * already-archived record and restoring a live
 * one are no-ops, so a repeated tap cannot
 * corrupt state. Records are immutable values;
 * every transition returns a copy.
 */
data class Archivable<T>(
    val item: T,
    val isArchived: Boolean = false,
)

object ArchiveRules {
    fun <T> archive(record: Archivable<T>): Archivable<T> {
        return record.copy(isArchived = true)
    }

    fun <T> restore(record: Archivable<T>): Archivable<T> {
        return record.copy(isArchived = false)
    }

    fun <T> visible(records: List<Archivable<T>>): List<Archivable<T>> {
        return records.filter { !it.isArchived }
    }

    fun <T> history(records: List<Archivable<T>>): List<Archivable<T>> {
        return records.toList()
    }
}
