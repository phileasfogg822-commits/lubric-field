package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.business.SearchGroup
import com.lubrcapp.lubrcapp.feature.sales.ItemOption
import com.lubrcapp.lubrcapp.feature.sales.PartyOption
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class DataSearchMapperTest {

    @Test
    fun documentsMapToInvoiceEntries() {
        val entries = DataSearchMapper.documentsToEntries(
            listOf(
                SalesDocument(
                    id = "d1",
                    type = SalesDocumentType.INVOICE,
                    number = "INV-1",
                    date = "2026-10-27",
                    status = "DRAFT",
                ),
            ),
        )
        assertEquals(1, entries.size)
        assertEquals(SearchGroup.INVOICES, entries.single().group)
        assertEquals("INV-1", entries.single().title)
    }

    @Test
    fun unnumberedDocumentFallsBackToId() {
        val entries = DataSearchMapper.documentsToEntries(
            listOf(SalesDocument(id = "d9", type = SalesDocumentType.INVOICE)),
        )
        assertEquals("d9", entries.single().title)
    }

    @Test
    fun itemsAndPartiesMapToTheirGroups() {
        val items = DataSearchMapper.itemsToEntries(
            listOf(
                ItemOption("i1", "Shell HX7", BigDecimal("500"), null),
                ItemOption("i2", "Priceless", null, null),
            ),
        )
        assertEquals(SearchGroup.ITEMS, items[0].group)
        assertEquals("500", items[0].detail)
        assertEquals("", items[1].detail)
        val parties = DataSearchMapper.partiesToEntries(
            listOf(PartyOption("p1", "Ali Autos")),
        )
        assertEquals(SearchGroup.CUSTOMERS, parties.single().group)
        assertEquals("Ali Autos", parties.single().title)
    }

    @Test
    fun documentsExportAsCsv() {
        val csv = DataSearchMapper.documentsToCsv(
            listOf(
                SalesDocument(
                    id = "d1",
                    type = SalesDocumentType.INVOICE,
                    number = "INV-1",
                    date = "2026-10-27",
                    status = "DRAFT",
                ),
            ),
        )
        assertEquals("Number,Date,Status\r\nINV-1,2026-10-27,DRAFT\r\n", csv)
    }
}
