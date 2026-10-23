package com.lubrcapp.lubrcapp.domain.business

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentNumberingTest {
    private val invoice = NumberingSequence(documentType = "INVOICE", prefix = "INV")
    private val jan2026: LocalDate = LocalDate.of(2026, 1, 15)

    @Test
    fun firstIssueMatchesTheSpecExample() {
        val (state, formatted) = DocumentNumbering.issue(invoice, NumberingState(), jan2026)
        assertEquals("INV-2026-00001", formatted)
        assertEquals(NumberingState(periodKey = "2026", lastIssued = 1), state)
    }

    @Test
    fun subsequentIssuesIncrementWithinThePeriod() {
        var state = NumberingState()
        var formatted = ""
        repeat(3) {
            val issued = DocumentNumbering.issue(invoice, state, jan2026)
            state = issued.first
            formatted = issued.second
        }
        assertEquals("INV-2026-00003", formatted)
        assertEquals(3L, state.lastIssued)
    }

    @Test
    fun yearlyResetRestartsAtTheStartingNumberInTheNewYear() {
        val state2026 = NumberingState(periodKey = "2026", lastIssued = 417)
        val (state, formatted) = DocumentNumbering.issue(invoice, state2026, LocalDate.of(2027, 1, 1))
        assertEquals("INV-2027-00001", formatted)
        assertEquals(NumberingState(periodKey = "2027", lastIssued = 1), state)
    }

    @Test
    fun monthlyResetUsesAYearMonthSegmentAndRestartsEachMonth() {
        val monthly = NumberingSequence(
            documentType = "RECEIPT",
            prefix = "RCPT",
            reset = NumberingReset.MONTHLY,
        )
        val october = DocumentNumbering.issue(monthly, NumberingState(), LocalDate.of(2026, 10, 3))
        assertEquals("RCPT-2026-10-00001", october.second)
        val november = DocumentNumbering.issue(monthly, october.first, LocalDate.of(2026, 11, 1))
        assertEquals("RCPT-2026-11-00001", november.second)
    }

    @Test
    fun neverResetHasNoPeriodSegmentAndNeverRestarts() {
        val never = NumberingSequence(
            documentType = "EXPENSE",
            prefix = "EXP",
            reset = NumberingReset.NEVER,
        )
        val state = NumberingState(periodKey = "", lastIssued = 9)
        val issued = DocumentNumbering.issue(never, state, LocalDate.of(2030, 6, 1))
        assertEquals("EXP-00010", issued.second)
    }

    @Test
    fun branchPrefixLeadsTheSegments() {
        val issued = DocumentNumbering.issue(invoice, NumberingState(), jan2026, branchPrefix = "LHR")
        assertEquals("LHR-INV-2026-00001", issued.second)
    }

    @Test
    fun suffixIsAppendedVerbatimAndCustomStartIsHonored() {
        val sequence = NumberingSequence(
            documentType = "QUOTATION",
            prefix = "QT",
            suffix = "/A",
            startingNumber = 100,
        )
        val issued = DocumentNumbering.issue(sequence, NumberingState(), jan2026)
        assertEquals("QT-2026-00100/A", issued.second)
    }

    @Test
    fun numbersWiderThanThePaddingAreNotTruncated() {
        val state = NumberingState(periodKey = "2026", lastIssued = 99_999)
        val issued = DocumentNumbering.issue(invoice, state, jan2026)
        assertEquals("INV-2026-100000", issued.second)
    }

    @Test
    fun sequencesForDifferentDocumentTypesAreIndependent() {
        val quotation = NumberingSequence(documentType = "QUOTATION", prefix = "QT")
        val issued = DocumentNumbering.issue(quotation, NumberingState(), jan2026)
        assertEquals("QT-2026-00001", issued.second)
    }
}
