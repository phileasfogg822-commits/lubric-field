package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.PaymentId
import com.lubrcapp.lubrcapp.domain.common.PaymentMethodId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAllocationTest {

    private val pkr = CurrencyCode.of("PKR")
    private val usd = CurrencyCode.of("USD")
    private val party = PartyId("party-1")
    private val otherParty = PartyId("party-2")

    private fun payment(
        amount: String,
        direction: PaymentDirection = PaymentDirection.RECEIVED,
        partyId: PartyId = party,
    ): Payment = Payment(
        id = PaymentId("pay-1"),
        tenantId = TenantId("tenant-1"),
        businessId = BusinessId("business-1"),
        partyId = partyId,
        direction = direction,
        methodId = PaymentMethodId("cash"),
        amount = Money.of(amount, pkr),
        date = LocalDate.of(2026, 10, 24),
        metadata = EntityMetadata.newLocal(
            Instant.parse("2026-10-24T00:00:00Z"),
            DeviceId("device-1"),
        ),
    )

    private fun openInvoice(
        id: String,
        grandTotal: String,
        alreadyPaid: String = "0",
        due: LocalDate = LocalDate.of(2026, 10, 20),
        partyId: PartyId = party,
        collectible: Boolean = true,
        currency: CurrencyCode = pkr,
    ): OpenInvoice = OpenInvoice(
        invoiceId = InvoiceId(id),
        partyId = partyId,
        grandTotal = Money.of(grandTotal, currency),
        alreadyPaid = Money.of(alreadyPaid, currency),
        dueDate = due,
        collectible = collectible,
    )

    @Test
    fun `full payment clears one invoice`() {
        val result = PaymentAllocation.allocate(
            payment("500"),
            listOf(openInvoice("inv-1", "500")),
        )
        assertEquals(1, result.applications.size)
        assertEquals(Money.of("500", pkr), result.applications[0].applied)
        assertTrue(result.applications[0].remaining.isZero)
        assertTrue(result.unallocated.isZero)
    }

    @Test
    fun `partial payment leaves a remainder`() {
        val result = PaymentAllocation.allocate(
            payment("200"),
            listOf(openInvoice("inv-1", "500")),
        )
        assertEquals(Money.of("200", pkr), result.applications[0].applied)
        assertEquals(Money.of("300", pkr), result.applications[0].remaining)
        assertTrue(result.unallocated.isZero)
    }

    @Test
    fun `payment spans invoices oldest first`() {
        val result = PaymentAllocation.allocate(
            payment("700"),
            listOf(
                openInvoice(
                    "inv-2",
                    "400",
                    due = LocalDate.of(2026, 10, 22),
                ),
                openInvoice(
                    "inv-1",
                    "500",
                    due = LocalDate.of(2026, 10, 20),
                ),
            ),
        )
        assertEquals(2, result.applications.size)
        assertEquals(InvoiceId("inv-1"), result.applications[0].invoiceId)
        assertEquals(Money.of("500", pkr), result.applications[0].applied)
        assertTrue(result.applications[0].remaining.isZero)
        assertEquals(InvoiceId("inv-2"), result.applications[1].invoiceId)
        assertEquals(Money.of("200", pkr), result.applications[1].applied)
        assertEquals(Money.of("200", pkr), result.applications[1].remaining)
        assertTrue(result.unallocated.isZero)
    }

    @Test
    fun `overpayment is reported unallocated`() {
        val result = PaymentAllocation.allocate(
            payment("800"),
            listOf(openInvoice("inv-1", "500")),
        )
        assertEquals(Money.of("500", pkr), result.applications[0].applied)
        assertEquals(Money.of("300", pkr), result.unallocated)
    }

    @Test
    fun `already paid reduces outstanding`() {
        val result = PaymentAllocation.allocate(
            payment("300"),
            listOf(openInvoice("inv-1", "500", alreadyPaid = "200")),
        )
        assertEquals(Money.of("300", pkr), result.applications[0].applied)
        assertTrue(result.applications[0].remaining.isZero)
    }

    @Test
    fun `fully paid invoice gets no application`() {
        val result = PaymentAllocation.allocate(
            payment("100"),
            listOf(openInvoice("inv-1", "500", alreadyPaid = "500")),
        )
        assertTrue(result.applications.isEmpty())
        assertEquals(Money.of("100", pkr), result.unallocated)
    }

    @Test
    fun `wrong party invoice is ignored`() {
        val result = PaymentAllocation.allocate(
            payment("100"),
            listOf(openInvoice("inv-1", "500", partyId = otherParty)),
        )
        assertTrue(result.applications.isEmpty())
        assertEquals(Money.of("100", pkr), result.unallocated)
    }

    @Test
    fun `non collectible invoice is skipped`() {
        val result = PaymentAllocation.allocate(
            payment("100"),
            listOf(openInvoice("inv-1", "500", collectible = false)),
        )
        assertTrue(result.applications.isEmpty())
    }

    @Test
    fun `paid direction allocates nothing`() {
        val result = PaymentAllocation.allocate(
            payment("100", direction = PaymentDirection.PAID),
            listOf(openInvoice("inv-1", "500")),
        )
        assertTrue(result.applications.isEmpty())
        assertEquals(Money.of("100", pkr), result.unallocated)
    }

    @Test
    fun `zero payment allocates nothing`() {
        val result = PaymentAllocation.allocate(
            payment("0"),
            listOf(openInvoice("inv-1", "500")),
        )
        assertTrue(result.applications.isEmpty())
        assertTrue(result.unallocated.isZero)
    }

    @Test
    fun `currency mismatch invoice is skipped`() {
        val result = PaymentAllocation.allocate(
            payment("100"),
            listOf(openInvoice("inv-1", "500", currency = usd)),
        )
        assertTrue(result.applications.isEmpty())
        assertEquals(Money.of("100", pkr), result.unallocated)
    }
}
