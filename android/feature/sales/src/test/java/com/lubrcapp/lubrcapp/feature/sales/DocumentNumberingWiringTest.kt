package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentNumberingWiringTest {

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableMap<String, String>>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(records(table))

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            records(table)

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            tables.getOrPut(table) { mutableMapOf() }[id] = json
        }

        private fun records(table: String): List<OperationalRecord> =
            tables[table].orEmpty().map { (id, json) -> OperationalRecord(id, json) }
    }

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val date: LocalDate = LocalDate.of(2026, 3, 5)

    @Test
    fun recordCodecRoundTripsAndDefaultsApply() {
        val record = NumberingRecord(
            id = "QUOTATION",
            documentType = "QUOTATION",
            prefix = "QT",
            suffix = "/A",
            startingNumber = 100,
            padding = 4,
            reset = "MONTHLY",
            periodKey = "2026-03",
            lastIssued = 107,
        )
        assertEquals(record, NumberingRecordJson.decode("QUOTATION", NumberingRecordJson.encode(record)))
        assertEquals("QT", DocumentNumberDefaults.recordFor(SalesDocumentType.QUOTATION).prefix)
        assertEquals("SO", DocumentNumberDefaults.recordFor(SalesDocumentType.SALES_ORDER).prefix)
    }

    @Test
    fun issuingNumbersAdvancesEachTypesOwnCounter() = runBlocking {
        val vm = SalesViewModel(FakeRepository())

        val first = vm.issueDocumentNumber(
            scope,
            SalesDocument(id = "q1", type = SalesDocumentType.QUOTATION),
            date,
        )
        assertEquals("QT-2026-00001", first.number)

        val second = vm.issueDocumentNumber(
            scope,
            SalesDocument(id = "q2", type = SalesDocumentType.QUOTATION),
            date,
        )
        assertEquals("QT-2026-00002", second.number)

        val order = vm.issueDocumentNumber(
            scope,
            SalesDocument(id = "o1", type = SalesDocumentType.SALES_ORDER),
            date,
        )
        assertEquals("SO-2026-00001", order.number)

        val stored = vm.loadNumberingRecord(scope, SalesDocumentType.QUOTATION)
        assertEquals(2L, stored.lastIssued)
        assertEquals("2026", stored.periodKey)
    }

    @Test
    fun anAlreadyNumberedDocumentKeepsItsNumberAndTheCounterStaysPut() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val numbered = SalesDocument(
            id = "q1",
            type = SalesDocumentType.QUOTATION,
            number = "QT-2025-00099",
        )
        val result = vm.issueDocumentNumber(scope, numbered, date)
        assertEquals("QT-2025-00099", result.number)
        assertEquals(null, fake.tables["numbering_sequences"])
    }

    @Test
    fun aCustomisedStoredRecordDrivesTheFormat() = runBlocking {
        val vm = SalesViewModel(FakeRepository())
        vm.saveNumberingRecord(
            scope,
            NumberingRecord(
                id = "QUOTATION",
                documentType = "QUOTATION",
                prefix = "EST",
                padding = 3,
                reset = "NEVER",
            ),
        )
        val issued = vm.issueDocumentNumber(
            scope,
            SalesDocument(id = "q1", type = SalesDocumentType.QUOTATION),
            date,
        )
        assertEquals("EST-001", issued.number)
    }
}
