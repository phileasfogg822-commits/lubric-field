package com.lubrcapp.lubrcapp.domain.purchase

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.PaymentId
import com.lubrcapp.lubrcapp.domain.common.PaymentMethodId
import com.lubrcapp.lubrcapp.domain.common.PurchaseId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.domain.payment.PaymentDirection
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchasePaymentApplicationTest {

    private val pkr = CurrencyCode.of("PKR")
    private val usd = CurrencyCode.of("USD")

    private fun invoice(status: PurchaseStatus = PurchaseStatus.RECEIVED, paid: String = "0") =
        PurchaseInvoice(
            id = PurchaseId("po-1"),
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-a"),
            partyId = PartyId("vendor-1"),
            invoiceNumber = "PINV-2026-00001",
            date = LocalDate.of(2026, 10, 25),
            status = status,
            subtotal = Money.of("10000", pkr),
            taxTotal = Money.of("1700", pkr),
            discount = Money.zero(pkr),
            total = Money.of("11700", pkr),
            paidAmount = Money.of(paid, pkr),
            metadata = EntityMetadata.newLocal(Instant.parse("2026-10-25T00:00:00Z"), DeviceId("device-a")),
        )

    private fun payment(
        id: String,
        amount: String,
        currency: CurrencyCode = pkr,
        direction: PaymentDirection = PaymentDirection.PAID,
        linked: String? = "po-1",
    ) = Payment(
        id = PaymentId(id),
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        partyId = PartyId("vendor-1"),
        direction = direction,
        methodId = PaymentMethodId("cash"),
        amount = Money.of(amount, currency),
        relatedInvoiceId = linked?.let { InvoiceId(it) },
        date = LocalDate.of(2026, 10, 25),
        metadata = EntityMetadata.newLocal(Instant.parse("2026-10-25T00:00:00Z"), DeviceId("device-a")),
    )

    private fun paidOf(result: PurchaseInvoice) = result.paidAmount.amount

    @Test fun partialPaymentMovesToPartiallyPaid() {
        val result = PurchasePaymentApplication.applyPayments(invoice(), listOf(payment("pay-1", "5000")))

        assertEquals(PurchaseStatus.PARTIALLY_PAID, result.status)
        assertEquals(0, BigDecimal("5000").compareTo(paidOf(result)))
        assertEquals(0, BigDecimal("6700").compareTo(result.balanceDue.amount))
    }

    @Test fun fullPaymentMovesToPaid() {
        val result = PurchasePaymentApplication.applyPayments(
            invoice(PurchaseStatus.PARTIALLY_PAID, "5000"),
            listOf(payment("pay-1", "5000"), payment("pay-2", "6700")),
        )

        assertEquals(PurchaseStatus.PAID, result.status)
        assertEquals(0, BigDecimal("11700").compareTo(paidOf(result)))
        assertEquals(0, BigDecimal("0").compareTo(result.balanceDue.amount))
    }

    @Test fun overpaymentLandsPaidWithNegativeBalance() {
        val result = PurchasePaymentApplication.applyPayments(invoice(), listOf(payment("pay-1", "12000")))

        assertEquals(PurchaseStatus.PAID, result.status)
        assertEquals(0, BigDecimal("-300").compareTo(result.balanceDue.amount))
    }

    @Test fun unlinkedAndReceivedPaymentsAreIgnored() {
        val result = PurchasePaymentApplication.applyPayments(
            invoice(),
            listOf(
                payment("pay-1", "5000"),
                payment("pay-2", "9000", linked = "po-other"),
                payment("pay-3", "9000", direction = PaymentDirection.RECEIVED),
                payment("pay-4", "9000", linked = null),
            ),
        )

        assertEquals(0, BigDecimal("5000").compareTo(paidOf(result)))
        assertEquals(PurchaseStatus.PARTIALLY_PAID, result.status)
    }

    @Test fun emptyPaymentSetLeavesInvoiceUnchanged() {
        val result = PurchasePaymentApplication.applyPayments(invoice(), emptyList())

        assertEquals(PurchaseStatus.RECEIVED, result.status)
        assertEquals(0, BigDecimal("0").compareTo(paidOf(result)))
    }

    @Test fun draftInvoiceRejectsPayments() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchasePaymentApplication.applyPayments(invoice(PurchaseStatus.DRAFT), listOf(payment("pay-1", "5000")))
        }
    }

    @Test fun rederivationOnPaidInvoiceIsIdempotent() {
        val result = PurchasePaymentApplication.applyPayments(
            invoice(PurchaseStatus.PAID, "11700"),
            listOf(payment("pay-1", "11700")),
        )

        assertEquals(PurchaseStatus.PAID, result.status)
        assertEquals(0, BigDecimal("11700").compareTo(paidOf(result)))
    }

    @Test fun mixedCurrencyPaymentsFail() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchasePaymentApplication.applyPayments(
                invoice(),
                listOf(payment("pay-1", "5000"), payment("pay-2", "100", currency = usd)),
            )
        }
    }
}
