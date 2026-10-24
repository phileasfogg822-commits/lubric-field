package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMovementRecordTest {

    private fun sample() = StockMovementRecord(
        id = "mv-1",
        itemId = "item-1",
        warehouseId = "wh-1",
        batchId = "batch-1",
        type = MovementType.SALE,
        quantity = BigDecimal("-5"),
        rate = BigDecimal("250.50"),
        referenceType = "SALES_ORDER",
        referenceId = "order-1",
        date = "2026-10-24",
        reason = "Sold",
        createdAt = "2026-10-24T00:00:00Z",
    )

    @Test
    fun codecRoundTripsEveryField() {
        val decoded = StockMovementJson.decode("mv-1", StockMovementJson.encode(sample()))
        assertEquals(sample(), decoded)
    }

    @Test
    fun recordIdWinsOverPayloadId() {
        val encoded = StockMovementJson.encode(sample())
        val decoded = StockMovementJson.decode("mv-other", encoded)
        assertEquals("mv-other", decoded.id)
    }

    @Test
    fun garbagePayloadDecodesToZeroMovement() {
        val decoded = StockMovementJson.decode("mv-9", "not json at all")
        assertEquals("mv-9", decoded.id)
        assertEquals(BigDecimal.ZERO, decoded.quantity)
        assertEquals(MovementType.ADJUSTMENT_IN, decoded.type)
        assertTrue(decoded.itemId.isEmpty())
    }

    @Test
    fun missingFieldsDefaultAndUnknownTypeIsNeutral() {
        val decoded = StockMovementJson.decode("mv-2", """{"itemId":"item-7","type":"SOMETHING_NEW","quantity":"3"}""")
        assertEquals("item-7", decoded.itemId)
        assertEquals(MovementType.ADJUSTMENT_IN, decoded.type)
        assertEquals(0, BigDecimal("3").compareTo(decoded.quantity))
        assertNull(decoded.warehouseId)
        assertNull(decoded.rate)
    }

    @Test
    fun ofAppliesTheSignFromTheType() {
        val sale = StockMovementRecord.of(id = "a", itemId = "i", type = MovementType.SALE, quantity = BigDecimal("5"))
        assertEquals(0, BigDecimal("-5").compareTo(sale.quantity))
        val purchase = StockMovementRecord.of(id = "b", itemId = "i", type = MovementType.PURCHASE, quantity = BigDecimal("-20"))
        assertEquals(0, BigDecimal("20").compareTo(purchase.quantity))
        val transferOut = StockMovementRecord.of(id = "c", itemId = "i", type = MovementType.TRANSFER_OUT, quantity = BigDecimal("2"))
        assertEquals(0, BigDecimal("-2").compareTo(transferOut.quantity))
    }

    @Test
    fun postingBridgeUsesPositiveQuantityAndKind() {
        val posting = sample().toPostingMovement(CurrencyCode.of("PKR"))
        assertEquals("item-1", posting.itemId.value)
        assertEquals("wh-1", posting.warehouseId?.value)
        assertEquals(StockMovementKind.OUT, posting.kind)
        assertEquals(0, BigDecimal("5").compareTo(posting.quantity.amount))
        assertEquals(0, BigDecimal("250.50").compareTo(posting.rate!!.amount))
        assertEquals("batch-1", posting.batchId)
    }

    @Test
    fun signMismatchIsDetectedButZeroIsNeutral() {
        val wrong = sample().copy(type = MovementType.PURCHASE) // quantity is -5, PURCHASE is inbound
        assertTrue(wrong.hasSignMismatch)
        val zero = sample().copy(quantity = BigDecimal.ZERO)
        assertTrue(!zero.hasSignMismatch)
    }
}
