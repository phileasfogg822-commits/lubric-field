package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class StockReservationTest {

    @Test
    fun reservationRoundTripsExactly() {
        val record = StockReservationRecord(
            id = "res-1",
            orderId = "o-1",
            lineId = "l-1",
            itemId = "item-1",
            warehouseId = "wh-1",
            batchId = "b-1",
            quantity = BigDecimal("10"),
            status = ReservationStatus.ACTIVE,
            createdAt = "2026-10-24T10:00:00Z",
        )
        assertEquals(record, StockReservationJson.decode("res-1", StockReservationJson.encode(record)))
    }

    @Test
    fun decodeIsTolerantAndTheRecordIdWins() {
        val decoded = StockReservationJson.decode(
            "record-id",
            """{"orderId":"o-1","quantity":"7.5","unknownField":true}""",
        )
        assertEquals("record-id", decoded.id)
        assertEquals("o-1", decoded.orderId)
        assertEquals(BigDecimal("7.5"), decoded.quantity)
        assertEquals(ReservationStatus.ACTIVE, decoded.status)

        val garbage = StockReservationJson.decode("record-id", "not json")
        assertEquals(
            StockReservationRecord(
                id = "record-id",
                orderId = "",
                lineId = "",
                itemId = "",
                quantity = BigDecimal.ZERO,
            ),
            garbage,
        )

        val unknownStatus = StockReservationJson.decode("record-id", """{"status":"HOLDING"}""")
        assertEquals(ReservationStatus.ACTIVE, unknownStatus.status)

        val unparseableQuantity = StockReservationJson.decode("record-id", """{"quantity":"many"}""")
        assertEquals(BigDecimal.ZERO, unparseableQuantity.quantity)
    }

    @Test
    fun availabilityMathSumsActiveOnlyPerDimension() {
        val reservations = listOf(
            StockReservationRecord(id = "r-1", orderId = "o-1", lineId = "l-1", itemId = "item-1", warehouseId = "wh-1", quantity = BigDecimal("10")),
            StockReservationRecord(id = "r-2", orderId = "o-2", lineId = "l-2", itemId = "item-1", warehouseId = "wh-1", quantity = BigDecimal("5")),
            StockReservationRecord(id = "r-3", orderId = "o-3", lineId = "l-3", itemId = "item-1", warehouseId = "wh-2", quantity = BigDecimal("7")),
            StockReservationRecord(id = "r-4", orderId = "o-4", lineId = "l-4", itemId = "item-1", warehouseId = "wh-1", quantity = BigDecimal("3"), status = ReservationStatus.RELEASED),
        )
        assertEquals(BigDecimal("15"), activeReservedQuantity(reservations, "item-1", "wh-1"))
        assertEquals(BigDecimal("7"), activeReservedQuantity(reservations, "item-1", "wh-2"))
        assertEquals(BigDecimal.ZERO, activeReservedQuantity(reservations, "item-2", "wh-1"))
        assertEquals(BigDecimal("85"), availableQuantity(BigDecimal("100"), BigDecimal("15")))
    }
}
