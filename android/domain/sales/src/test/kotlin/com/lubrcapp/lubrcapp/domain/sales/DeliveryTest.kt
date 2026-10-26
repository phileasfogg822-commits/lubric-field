package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.OrderId
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryTest {

    private fun schedule(): DeliverySchedule = DeliveryOps.schedule(
        id = "sch-1",
        orderId = OrderId("so-1"),
        partyId = PartyId("ali-autos"),
        expectedDate = "2026-10-28",
        routeId = "route-1",
        deliveryAddress = "Main Bazaar",
        vehicle = "Truck 1",
        driver = "Ahmed",
        notes = "Call before delivery",
    )

    @Test
    fun scheduleKeepsAllFields() {
        val scheduled = schedule()
        assertEquals(OrderId("so-1"), scheduled.orderId)
        assertEquals(PartyId("ali-autos"), scheduled.partyId)
        assertEquals("2026-10-28", scheduled.expectedDate)
        assertEquals("route-1", scheduled.routeId)
        assertEquals("Main Bazaar", scheduled.deliveryAddress)
        assertEquals("Truck 1", scheduled.vehicle)
        assertEquals("Ahmed", scheduled.driver)
        assertEquals("Call before delivery", scheduled.notes)
    }

    @Test
    fun scheduleDefaultsToEmptyOptionals() {
        val scheduled = DeliveryOps.schedule(
            id = "sch-1",
            orderId = OrderId("so-1"),
            partyId = PartyId("ali-autos"),
            expectedDate = "2026-10-28",
        )
        assertEquals("", scheduled.routeId)
        assertEquals("", scheduled.deliveryAddress)
        assertEquals("", scheduled.vehicle)
        assertEquals("", scheduled.driver)
        assertEquals("", scheduled.notes)
    }

    @Test
    fun scheduleThrowsOnBlankExpectedDate() {
        assertThrows(IllegalArgumentException::class.java) {
            DeliveryOps.schedule(
                id = "sch-1",
                orderId = OrderId("so-1"),
                partyId = PartyId("ali-autos"),
                expectedDate = "  ",
            )
        }
    }

    @Test
    fun challanForMapsScheduleFields() {
        val challan = DeliveryOps.challanFor(
            schedule(),
            challanId = "dc-1",
            number = "DC-1",
            date = "2026-10-28",
        )
        assertEquals("DC-1", challan.number)
        assertEquals(OrderId("so-1"), challan.orderId)
        assertEquals(PartyId("ali-autos"), challan.partyId)
        assertEquals("Main Bazaar", challan.shippingAddress)
        assertEquals("2026-10-28", challan.date)
        assertTrue(challan.lines.isEmpty())
    }

    @Test
    fun challanDefaultsToUnnumbered() {
        val challan = DeliveryOps.challanFor(schedule(), challanId = "dc-1")
        assertEquals("", challan.number)
        assertEquals("", challan.date)
    }

    @Test
    fun challanKeepsLines() {
        val lines = listOf(
            DeliveryChallanLine(
                id = "cl-1",
                itemId = ItemId("oil-1"),
                description = "Engine Oil",
                quantity = Quantity.of("10"),
            ),
            DeliveryChallanLine(
                id = "cl-2",
                description = "Unidentified item",
                quantity = Quantity.of("2"),
            ),
        )
        val challan = DeliveryOps.challanFor(schedule(), challanId = "dc-1", lines = lines)
        assertEquals(listOf("cl-1", "cl-2"), challan.lines.map { it.id })
        assertEquals(Quantity.of("10"), challan.lines[0].quantity)
        assertEquals(null, challan.lines[1].itemId)
    }
}
