package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.payment.InvoiceApplication
import com.lubrcapp.lubrcapp.domain.sales.SalesStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// STAGED for 22.2 — targets feature/sales tests once
// applyPayment is merged into SalesDocumentStatus and the
// `:domain:payment` dependency is added at integration.
class SalesDocumentPaymentStatusTest {

    private val pkr = CurrencyCode.of("PKR")

    private fun invoice(status: String): SalesDocument =
        SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, status = status)

    private fun application(applied: String, remaining: String): InvoiceApplication =
        InvoiceApplication(
            invoiceId = InvoiceId("inv-1"),
            applied = Money.of(applied, pkr),
            remaining = Money.of(remaining, pkr),
        )

    @Test
    fun `sent plus full payment becomes paid`() {
        val moved = SalesDocumentStatus.applyPayment(
            invoice("SENT"),
            application("500", "0"),
        )
        assertEquals(SalesStatus.PAID.name, moved.status)
    }

    @Test
    fun `sent plus partial payment becomes partially paid`() {
        val moved = SalesDocumentStatus.applyPayment(
            invoice("SENT"),
            application("200", "300"),
        )
        assertEquals(SalesStatus.PARTIALLY_PAID.name, moved.status)
    }

    @Test
    fun `partially paid plus full payment becomes paid`() {
        val moved = SalesDocumentStatus.applyPayment(
            invoice("PARTIALLY_PAID"),
            application("300", "0"),
        )
        assertEquals(SalesStatus.PAID.name, moved.status)
    }

    @Test
    fun `partially paid plus partial payment stays partially paid`() {
        val moved = SalesDocumentStatus.applyPayment(
            invoice("PARTIALLY_PAID"),
            application("100", "200"),
        )
        assertEquals(SalesStatus.PARTIALLY_PAID.name, moved.status)
    }

    @Test
    fun `overdue plus full payment becomes paid`() {
        val moved = SalesDocumentStatus.applyPayment(
            invoice("OVERDUE"),
            application("500", "0"),
        )
        assertEquals(SalesStatus.PAID.name, moved.status)
    }

    @Test
    fun `paid invoice refuses further payments`() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.applyPayment(
                invoice("PAID"),
                application("100", "0"),
            )
        }
    }

    @Test
    fun `draft invoice refuses payments`() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.applyPayment(
                invoice("DRAFT"),
                application("100", "0"),
            )
        }
    }

    @Test
    fun `non invoice refuses payments`() {
        val order = SalesDocument(
            id = "ord-1",
            type = SalesDocumentType.SALES_ORDER,
            status = "CONFIRMED",
        )
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.applyPayment(
                order,
                InvoiceApplication(
                    invoiceId = InvoiceId("ord-1"),
                    applied = Money.of("100", pkr),
                    remaining = Money.of("0", pkr),
                ),
            )
        }
    }

    @Test
    fun `application for another invoice refuses`() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.applyPayment(
                invoice("SENT"),
                InvoiceApplication(
                    invoiceId = InvoiceId("inv-2"),
                    applied = Money.of("100", pkr),
                    remaining = Money.of("0", pkr),
                ),
            )
        }
    }

    @Test
    fun `zero application leaves document unchanged`() {
        val moved = SalesDocumentStatus.applyPayment(
            invoice("SENT"),
            application("0", "500"),
        )
        assertEquals("SENT", moved.status)
    }
}
