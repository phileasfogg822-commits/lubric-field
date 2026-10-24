package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalEntryRecordTest {

    private fun balancedEntry(): JournalEntryRecord = JournalEntryRecord(
        id = "je-1",
        date = "2026-10-24",
        description = "Credit sale",
        referenceNumber = "INV-2026-00001",
        lines = listOf(
            JournalLineRecord("receivable", BigDecimal("100000"), BigDecimal.ZERO),
            JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
        ),
        createdAt = "2026-10-24T00:00:00Z",
    )

    @Test
    fun balanceTotalsUseBigDecimalComparison() {
        val entry = balancedEntry()
        assertEquals(BigDecimal("100000"), entry.totalDebits)
        assertEquals(BigDecimal("100000"), entry.totalCredits)
        assertTrue(entry.isBalanced)
    }

    @Test
    fun unbalancedEntryReportsNotBalanced() {
        val entry = balancedEntry().copy(
            lines = listOf(
                JournalLineRecord("receivable", BigDecimal("100000"), BigDecimal.ZERO),
                JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("99999")),
            ),
        )
        assertFalse(entry.isBalanced)
    }

    @Test
    fun codecRoundTripPreservesEntry() {
        val entry = balancedEntry()
        val decoded = JournalEntryJson.decode(entry.id, JournalEntryJson.encode(entry))
        assertEquals(entry, decoded)
    }

    @Test
    fun codecToleratesGarbageAndUnknownFields() {
        val decoded = JournalEntryJson.decode("je-9", "{not json")
        assertEquals("je-9", decoded.id)
        assertEquals(emptyList<JournalLineRecord>(), decoded.lines)

        val withUnknown = JournalEntryJson.decode(
            "je-9",
            """{"date":"2026-10-24","description":"X","surprise":true,"lines":[]}""",
        )
        assertEquals("2026-10-24", withUnknown.date)
        assertEquals("X", withUnknown.description)
    }

    @Test
    fun codecUnparseableAmountDecodesAsZeroAndRecordIdWins() {
        val decoded = JournalEntryJson.decode(
            "je-9",
            """{"id":"someone-else","lines":[{"accountId":"cash","debit":"oops","credit":"50"}]}""",
        )
        assertEquals("je-9", decoded.id)
        assertEquals(BigDecimal.ZERO, decoded.lines[0].debit)
        assertEquals(BigDecimal("50"), decoded.lines[0].credit)
    }

    @Test
    fun domainBridgeMapsLinesWithCallerCurrency() {
        val domain = balancedEntry().toDomainEntry(
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-a"),
            currency = CurrencyCode.of("PKR"),
            deviceId = DeviceId("device-a"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
        )
        assertEquals("je-1", domain.id.value)
        assertEquals("2026-10-24", domain.date.toString())
        assertEquals("INV-2026-00001", domain.referenceNumber)
        assertEquals(2, domain.lines.size)
        assertEquals("je-1-0", domain.lines[0].id.value)
        assertEquals("receivable", domain.lines[0].accountId.value)
        assertEquals("100000", domain.lines[0].debit.amount.toPlainString())
        assertEquals("PKR", domain.lines[0].debit.currency.value)
        assertEquals("sales", domain.lines[1].accountId.value)
        assertEquals("100000", domain.lines[1].credit.amount.toPlainString())
    }

    @Test
    fun factoryStampsFreshIdAndTimestamp() {
        val entry = JournalEntryRecord.of(
            date = "2026-10-24",
            description = "Cash receipt",
            lines = listOf(JournalLineRecord("cash", BigDecimal("500"), BigDecimal.ZERO)),
        )
        assertTrue(entry.id.isNotBlank())
        assertTrue(entry.createdAt.isNotBlank())
        assertEquals("2026-10-24", entry.date)
    }
}
