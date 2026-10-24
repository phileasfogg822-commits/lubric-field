package com.lubrcapp.lubrcapp.feature.sales

/**
 * Phase 18.4 — a §14 timeline entry: the first concrete writer
 * of the activity timeline the 17.5 contract note anticipated.
 * One entry is written whenever a document is saved or
 * converted, carrying the vocabulary the posting contract
 * fixed in 17.4 (document type, number, party, totals).
 *
 * Entries are "timeline_entries" operational records. That
 * table is NOT in the Phase 12 sync whitelist, so the timeline
 * is local-only until the sync layer learns it — stated here
 * rather than discovered later.
 */
data class TimelineEntry(
    val id: String,
    val documentId: String,
    /** QUOTATION / SALES_ORDER (the SalesDocumentType name). */
    val documentType: String,
    /** The document's number at the time of the entry; may be blank. */
    val documentNumber: String = "",
    val partyId: String? = null,
    /** SAVED or CONVERTED. */
    val action: String,
    /** Document grand total as a plain string, as computed at save time. */
    val grandTotal: String = "",
    /** ISO-8601 instant text (Instant.toString()) of when it happened. */
    val timestamp: String = "",
)
