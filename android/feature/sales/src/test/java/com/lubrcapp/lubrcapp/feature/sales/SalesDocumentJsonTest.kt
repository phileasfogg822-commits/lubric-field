package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesDocumentJsonTest {

    @Test
    fun headerRoundTripsExactly() {
        val document = SalesDocument(
            id = "doc-1",
            type = SalesDocumentType.QUOTATION,
            number = "QT-2026-00001",
            partyId = "party-1",
            date = "2026-10-23",
            dueDate = "2026-11-06",
            status = "SENT",
            notes = "Validity 14 days",
            convertedFromId = null,
            convertedToId = "doc-2",
        )
        assertEquals(document, SalesDocumentJson.decode("doc-1", SalesDocumentJson.encode(document)))
    }

    @Test
    fun invoiceTypeRoundTripsExactly() {
        val document = SalesDocument(
            id = "inv-1",
            type = SalesDocumentType.INVOICE,
            number = "INV-2026-00001",
            partyId = "party-1",
            date = "2026-10-24",
            dueDate = "2026-11-23",
            status = "SENT",
            notes = "Converted from order doc-2",
            convertedFromId = "doc-2",
            convertedToId = null,
        )
        assertEquals(document, SalesDocumentJson.decode("inv-1", SalesDocumentJson.encode(document)))
    }

    @Test
    fun headerDecodeIsTolerantAndTheRecordIdWins() {
        val decoded = SalesDocumentJson.decode(
            "record-id",
            """{"type":"SALES_ORDER","number":42,"unknownField":true}""",
        )
        assertEquals("record-id", decoded.id)
        assertEquals(SalesDocumentType.SALES_ORDER, decoded.type)
        assertEquals("42", decoded.number)
        assertEquals("DRAFT", decoded.status)
        assertNull(decoded.partyId)

        val garbage = SalesDocumentJson.decode("record-id", "not json")
        assertEquals(SalesDocument(id = "record-id", type = SalesDocumentType.SALES_ORDER), garbage)

        val unknownType = SalesDocumentJson.decode("record-id", """{"type":"ESTIMATE"}""")
        assertEquals(SalesDocumentType.SALES_ORDER, unknownType.type)
    }

    @Test
    fun lineRoundTripsExactlyIncludingTheTaxSnapshot() {
        val line = SalesDocumentLine(
            id = "line-1",
            orderId = "doc-1",
            position = 2,
            itemId = "item-1",
            description = "Engine Oil 4L",
            quantity = BigDecimal("3"),
            unitId = "unit-bottle",
            rate = BigDecimal("4500.50"),
            discountKind = "PERCENT",
            discountValue = BigDecimal("10"),
            taxInclusive = false,
            taxComponents = listOf(
                SalesTaxComponent("Sales Tax", BigDecimal("17")),
                SalesTaxComponent("Further Tax", BigDecimal("3")),
            ),
            warehouseId = "wh-1",
            batchId = "batch-1",
            fulfilledQuantity = BigDecimal("1"),
        )
        assertEquals(line, SalesDocumentLineJson.decode("line-1", SalesDocumentLineJson.encode(line)))
    }

    @Test
    fun lineSerialNumbersRoundTripAndTolerateMissing() {
        val line = SalesDocumentLine(
            id = "line-1",
            orderId = "doc-1",
            quantity = BigDecimal("1"),
            rate = BigDecimal("100"),
            serialNumbers = listOf("SN1", "SN2"),
        )
        assertEquals(line, SalesDocumentLineJson.decode("line-1", SalesDocumentLineJson.encode(line)))

        val missing = SalesDocumentLineJson.decode(
            "line-1",
            """{"orderId":"doc-1","quantity":"1","rate":"100"}""",
        )
        assertEquals(emptyList<String>(), missing.serialNumbers)

        val nonArray = SalesDocumentLineJson.decode("line-1", """{"serialNumbers":"SN1"}""")
        assertEquals(emptyList<String>(), nonArray.serialNumbers)

        val blanks = SalesDocumentLineJson.decode("line-1", """{"serialNumbers":["SN1","","SN2"]}""")
        assertEquals(listOf("SN1", "SN2"), blanks.serialNumbers)
    }

    @Test
    fun lineDecodeAcceptsJsonNumbersAndDropsDamagedTaxComponents() {
        val decoded = SalesDocumentLineJson.decode(
            "line-9",
            """{"orderId":"doc-1","quantity":2,"rate":100.25,"taxComponents":[{"name":"VAT","rate":5},{"name":"","rate":9},{"name":"Bad","rate":"x"}]}""",
        )
        assertEquals(BigDecimal("2"), decoded.quantity)
        assertEquals(BigDecimal("100.25"), decoded.rate)
        assertEquals(listOf(SalesTaxComponent("VAT", BigDecimal("5"))), decoded.taxComponents)
        assertEquals(BigDecimal.ZERO, decoded.fulfilledQuantity)
    }
}
