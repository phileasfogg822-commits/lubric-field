package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.OrderId
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Quantity

/**
 * Phase 27.4 — delivery scheduling (§46) + delivery
 * challan (§47). Pure model, no persistence here: how
 * schedules and challans are stored is a later package
 * decision against the record machinery and the sync
 * whitelist.
 *
 * §46 fields: Order Date, Expected Delivery, Route,
 * Delivery Address, Vehicle, Driver, Notes. The order
 * date lives on the sales order itself, so the schedule
 * carries the expected date plus the route, address,
 * vehicle, driver and notes. Route is the 27.1 route id
 * (a plain string: the schedule references a route, it
 * does not own one). Vehicle and driver are plain
 * strings — no typed vehicle/driver id exists in
 * domain/common, and inventing one is out of scope.
 *
 * §47 fields: Challan Number, Customer, Shipping
 * Address, Date (+ lines). The challan is built FROM
 * a schedule ([DeliveryOps.challanFor]), so customer,
 * order and shipping address can never disagree with
 * the schedule they came from. The number is a plain
 * string, empty until assigned: no DC prefix is
 * invented here — numbering (NumberingRecord's
 * QT/SO/INV/SR/CN set has no DC) is a shell /
 * numbering decision, not a model one.
 *
 * Execution (delivered / failed / partial, §161–§163)
 * is 27.5, not here: this package schedules and
 * documents, it does not mark outcomes.
 */
data class DeliverySchedule(
    val id: String,
    val orderId: OrderId,
    val partyId: PartyId,
    val expectedDate: String,
    val routeId: String = "",
    val deliveryAddress: String = "",
    val vehicle: String = "",
    val driver: String = "",
    val notes: String = "",
)

data class DeliveryChallanLine(
    val id: String,
    val itemId: ItemId? = null,
    val description: String = "",
    val quantity: Quantity,
)

data class DeliveryChallan(
    val id: String,
    val number: String = "",
    val orderId: OrderId,
    val partyId: PartyId,
    val shippingAddress: String = "",
    val date: String = "",
    val lines: List<DeliveryChallanLine> = emptyList(),
)

object DeliveryOps {
    fun schedule(
        id: String,
        orderId: OrderId,
        partyId: PartyId,
        expectedDate: String,
        routeId: String = "",
        deliveryAddress: String = "",
        vehicle: String = "",
        driver: String = "",
        notes: String = "",
    ): DeliverySchedule {
        require(expectedDate.isNotBlank()) {
            "Delivery schedule needs an expected date: $id"
        }
        return DeliverySchedule(
            id = id,
            orderId = orderId,
            partyId = partyId,
            expectedDate = expectedDate,
            routeId = routeId,
            deliveryAddress = deliveryAddress,
            vehicle = vehicle,
            driver = driver,
            notes = notes,
        )
    }

    fun challanFor(
        schedule: DeliverySchedule,
        challanId: String,
        number: String = "",
        date: String = "",
        lines: List<DeliveryChallanLine> = emptyList(),
    ): DeliveryChallan = DeliveryChallan(
        id = challanId,
        number = number,
        orderId = schedule.orderId,
        partyId = schedule.partyId,
        shippingAddress = schedule.deliveryAddress,
        date = date,
        lines = lines,
    )
}
