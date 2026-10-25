package com.lubrcapp.lubrcapp.feature.purchases

/**
 * Phase 23.6 (STAGED — integrates after 23.5 accepts) —
 * the result of a purchases posting-join call, the
 * mirror of feature/sales' SalesPostResult. Posted
 * carries the document number for the screen's
 * notice; Rejected carries the validator's
 * violation names. No serials: purchase postings
 * flip no serials in this phase.
 */
sealed interface PurchasePostResult {
    data class Posted(
        val documentNumber: String,
    ) : PurchasePostResult

    data class Rejected(
        val reasons: List<String>,
    ) : PurchasePostResult
}
