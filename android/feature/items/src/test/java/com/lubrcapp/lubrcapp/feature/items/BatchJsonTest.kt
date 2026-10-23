package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.4: the batch codec round-trips every §29 field and tolerates legacy payloads. */
class BatchJsonTest {

    @Test fun fullBatchRoundTrip() {
        val batch = Batch(
            id = "bt-1",
            itemId = "i-1",
            batchNumber = "B-2026-01",
            manufacturingDate = "2026-01-15",
            expiryDate = "2027-01-14",
            purchaseRate = BigDecimal("800"),
            saleRate = BigDecimal("1100"),
            mrp = BigDecimal("1250"),
            quantity = BigDecimal("48"),
        )
        assertEquals(batch, BatchJson.decode("bt-1", BatchJson.encode(batch)))
    }

    @Test fun numbersAcceptedAsJsonNumbersAndPrecisionKept() {
        val decoded = BatchJson.decode(
            "bt-2",
            """{"itemId":"i-1","batchNumber":"B2","quantity":12.5,"purchaseRate":"0.10"}""",
        )
        assertEquals(BigDecimal("12.5"), decoded.quantity)
        assertEquals(BigDecimal("0.10"), decoded.purchaseRate)
    }

    @Test fun minimalAndBadPayloadsDecodeMinimal() {
        val legacy = BatchJson.decode("bt-7", """{"uuid":"x","itemId":"i-9","batchNumber":"Old"}""")
        assertEquals("i-9", legacy.itemId)
        assertEquals("Old", legacy.batchNumber)
        assertEquals(null, legacy.quantity)
        val bad = BatchJson.decode("bt-8", "junk")
        assertEquals("", bad.itemId)
        assertEquals("", bad.batchNumber)
        assertEquals("", BatchJson.decode("bt-9", "").batchNumber)
    }
}
