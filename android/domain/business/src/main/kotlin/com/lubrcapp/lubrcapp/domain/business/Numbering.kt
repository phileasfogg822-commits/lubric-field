package com.lubrcapp.lubrcapp.domain.business

import java.time.LocalDate

/**
 * Automatic document numbering (master spec §51). Each document type has
 * an INDEPENDENT sequence; the configuration is data, so prefixes,
 * starting numbers and reset rules are per-business settings, never
 * hard-coded per document type in the engine.
 *
 * Format: the non-empty segments
 * `[branchPrefix, prefix, periodSegment, paddedNumber]` joined with "-",
 * with the suffix appended verbatim (the suffix carries its own
 * separator if it wants one). The period segment is "yyyy" for a yearly
 * reset, "yyyy-MM" for monthly, and absent when the sequence never
 * resets — e.g. prefix "INV", yearly, number 1 in 2026 gives
 * "INV-2026-00001", matching the spec examples.
 */
enum class NumberingReset {
    NEVER,
    YEARLY,
    MONTHLY,
}

data class NumberingSequence(
    val documentType: String,
    val prefix: String,
    val suffix: String = "",
    val startingNumber: Long = 1,
    val padding: Int = 5,
    val reset: NumberingReset = NumberingReset.YEARLY,
) {
    init {
        require(documentType.isNotBlank()) { "Document type is required" }
        require(startingNumber >= 1) { "Starting number must be at least 1" }
        require(padding in 1..12) { "Padding must be between 1 and 12" }
    }

    /** The period a date falls in, under this sequence's reset rule. */
    fun periodKey(date: LocalDate): String = when (reset) {
        NumberingReset.NEVER -> ""
        NumberingReset.YEARLY -> date.year.toString()
        NumberingReset.MONTHLY -> "%04d-%02d".format(date.year, date.monthValue)
    }
}

/**
 * The persisted counter for one sequence: which period [lastIssued]
 * belongs to, and the last number handed out in it (0 = none yet).
 *
 * HONEST LIMIT: this state is device-local in LOCAL_ONLY mode. Two
 * devices can hand out the same number for the same document type;
 * cross-device uniqueness in CLOUD mode is a Phase 30 concern and this
 * contract does not pretend otherwise.
 */
data class NumberingState(
    val periodKey: String = "",
    val lastIssued: Long = 0,
)

object DocumentNumbering {

    /** The next number for [state] on [date], without formatting it. */
    fun nextNumber(sequence: NumberingSequence, state: NumberingState, date: LocalDate): Long =
        if (state.periodKey != sequence.periodKey(date)) {
            // A new period (or the first issue ever) restarts the sequence.
            sequence.startingNumber
        } else {
            maxOf(state.lastIssued + 1, sequence.startingNumber)
        }

    fun format(
        sequence: NumberingSequence,
        number: Long,
        date: LocalDate,
        branchPrefix: String? = null,
    ): String {
        val segments = listOfNotNull(
            branchPrefix?.takeIf { it.isNotBlank() },
            sequence.prefix.takeIf { it.isNotBlank() },
            sequence.periodKey(date).takeIf { it.isNotEmpty() },
            number.toString().padStart(sequence.padding, '0'),
        )
        return segments.joinToString("-") + sequence.suffix
    }

    /**
     * Issue the next number: returns the advanced state (persist it) and
     * the formatted document number.
     */
    fun issue(
        sequence: NumberingSequence,
        state: NumberingState,
        date: LocalDate,
        branchPrefix: String? = null,
    ): Pair<NumberingState, String> {
        val number = nextNumber(sequence, state, date)
        val advanced = NumberingState(periodKey = sequence.periodKey(date), lastIssued = number)
        return advanced to format(sequence, number, date, branchPrefix)
    }
}
