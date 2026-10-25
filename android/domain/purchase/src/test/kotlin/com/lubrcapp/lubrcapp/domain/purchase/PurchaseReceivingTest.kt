package com.lubrcapp.lubrcapp.domain.purchase

import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.PurchaseId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.TransactionLineId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseReceivingTest {

    private val pkr = CurrencyCode.of("PKR")

    private fun invoice(status: PurchaseStatus = PurchaseStatus.DRAFT) = PurchaseInvoice(
        id = PurchaseId("po-1"),
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        partyId = PartyId("vendor-1"),
        invoiceNumber = "PINV-2026-00001",
        date = LocalDate.of(2026, 10, 25),
        status = status,
        subtotal = Money.of("15000", pkr),
        taxTotal = Money.zero(pkr),
        discount = Money.zero(pkr),
        total = Money.of("15000", pkr),
        paidAmount = Money.zero(pkr),
        metadata = EntityMetadata.newLocal(Instant.parse("2026-10-25T00:00:00Z"), DeviceId("device-a")),
    )

    private fun lines() = listOf(
        PurchaseInvoiceLine(
            id = TransactionLineId("l-1"),
            purchaseId = PurchaseId("po-1"),
            itemId = ItemId("item-1"),
            quantity = Quantity.of(BigDecimal("2")),
            unitPrice = Money.of("5000", pkr),
            taxRate = Percentage.ZERO,
            discount = Money.zero(pkr),
            lineTotal = Money.of("10000", pkr),
        ),
        PurchaseInvoiceLine(
            id = TransactionLineId("l-2"),
            purchaseId = PurchaseId("po-1"),
            itemId = ItemId("item-2"),
            quantity = Quantity.of(BigDecimal("5")),
            unitPrice = Money.of("1000", pkr),
            taxRate = Percentage.ZERO,
            discount = Money.zero(pkr),
            lineTotal = Money.of("5000", pkr),
        ),
    )

    private fun qty(value: String) = Quantity.of(BigDecimal(value))

    @Test fun fullReceiptKeepsLinesAndEmitsInMovements() {
        val result = PurchaseReceiving.receive(
            invoice(),
            lines(),
            mapOf("l-1" to qty("2"), "l-2" to qty("5")),
        )

        assertEquals(PurchaseStatus.RECEIVED, result.invoice.status)
        assertEquals("po-1", result.invoice.id.value)
        assertEquals(0, BigDecimal("2").compareTo(result.lines[0].quantity.amount))
        assertEquals(0, BigDecimal("5").compareTo(result.lines[1].quantity.amount))
        assertEquals(2, result.movements.size)
        assertTrue(result.movements.all { it.kind == StockMovementKind.IN })
        assertEquals(0, BigDecimal("2").compareTo(result.movements[0].quantity.amount))
        assertEquals(0, BigDecimal("5000").compareTo(result.movements[0].rate!!.amount))
        assertEquals(0, BigDecimal("5").compareTo(result.movements[1].quantity.amount))
        assertNull(result.movements[0].warehouseId)
    }

    @Test fun shortDeliveryAdjustsLinesAndSkipsZeroMovements() {
        val result = PurchaseReceiving.receive(
            invoice(),
            lines(),
            mapOf("l-1" to qty("1")),
        )

        assertEquals(0, BigDecimal("1").compareTo(result.lines[0].quantity.amount))
        assertEquals(0, BigDecimal("0").compareTo(result.lines[1].quantity.amount))
        assertEquals(1, result.movements.size)
        assertEquals("item-1", result.movements[0].itemId.value)
    }

    @Test fun overDeliveryAdjustsLineUpward() {
        val result = PurchaseReceiving.receive(
            invoice(),
            lines(),
            mapOf("l-1" to qty("3"), "l-2" to qty("5")),
        )

        assertEquals(0, BigDecimal("3").compareTo(result.lines[0].quantity.amount))
        assertEquals(0, BigDecimal("3").compareTo(result.movements[0].quantity.amount))
    }

    @Test fun nonDraftPurchaseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseReceiving.receive(invoice(PurchaseStatus.RECEIVED), lines(), mapOf("l-1" to qty("2")))
        }
    }

    @Test fun negativeReceivedQuantityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseReceiving.receive(invoice(), lines(), mapOf("l-1" to qty("-1")))
        }
    }
}
