package com.lubrcapp.lubrcapp.core.repository

enum class ProtectedHistoryType {
    POSTED_INVOICE,
    PAYMENT,
    STOCK_MOVEMENT,
    JOURNAL_ENTRY,
    CASH_COLLECTION,
}

enum class HistoryMutation {
    APPEND,
    REVERSE,
    CORRECT,
    DIRECT_EDIT,
    DIRECT_DELETE,
}

object HistoryMutationPolicy {
    fun isAllowed(mutation: HistoryMutation): Boolean =
        when (mutation) {
            HistoryMutation.APPEND,
            HistoryMutation.REVERSE,
            HistoryMutation.CORRECT -> true
            HistoryMutation.DIRECT_EDIT,
            HistoryMutation.DIRECT_DELETE -> false
        }

    fun requireAllowed(
        recordType: ProtectedHistoryType,
        mutation: HistoryMutation,
        reason: String? = null,
    ) {
        require(isAllowed(mutation)) {
            "$recordType is append-oriented; use reversal or correction instead of direct mutation"
        }

        if (mutation == HistoryMutation.REVERSE || mutation == HistoryMutation.CORRECT) {
            require(!reason.isNullOrBlank()) {
                "A reason is required for $mutation of $recordType"
            }
        }
    }
}
