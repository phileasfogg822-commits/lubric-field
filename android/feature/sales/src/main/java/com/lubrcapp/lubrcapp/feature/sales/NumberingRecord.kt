package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.business.NumberingReset
import com.lubrcapp.lubrcapp.domain.business.NumberingSequence
import com.lubrcapp.lubrcapp.domain.business.NumberingState

/**
 * Phase 18.3 — the persisted numbering configuration + counter
 * for one document type, stored as a "numbering_sequences"
 * operational record whose id is the document-type key.
 *
 * Stated plainly (again): "numbering_sequences" is NOT in the
 * Phase 12 sync whitelist, so this record is local-only until the
 * sync layer learns the table — two devices can still hand out
 * the same number in LOCAL_ONLY mode; Phase 30 owns cross-device
 * uniqueness. The 17.2 contract carries the same warning.
 */
data class NumberingRecord(
    val id: String,
    val documentType: String,
    val prefix: String,
    val suffix: String = "",
    val startingNumber: Long = 1,
    val padding: Int = 5,
    val reset: String = "YEARLY",
    val periodKey: String = "",
    val lastIssued: Long = 0,
) {
    fun sequence(): NumberingSequence = NumberingSequence(
        documentType = documentType,
        prefix = prefix,
        suffix = suffix,
        startingNumber = startingNumber,
        padding = padding,
        reset = NumberingReset.entries.firstOrNull { it.name == reset } ?: NumberingReset.YEARLY,
    )

    fun state(): NumberingState = NumberingState(periodKey = periodKey, lastIssued = lastIssued)

    fun withState(state: NumberingState): NumberingRecord =
        copy(periodKey = state.periodKey, lastIssued = state.lastIssued)
}

/** The §51 factory defaults, used until a stored record exists. */
object DocumentNumberDefaults {
    fun recordFor(type: SalesDocumentType): NumberingRecord = when (type) {
        SalesDocumentType.QUOTATION -> NumberingRecord(
            id = type.name,
            documentType = type.name,
            prefix = "QT",
        )
        SalesDocumentType.SALES_ORDER -> NumberingRecord(
            id = type.name,
            documentType = type.name,
            prefix = "SO",
        )
    }
}
