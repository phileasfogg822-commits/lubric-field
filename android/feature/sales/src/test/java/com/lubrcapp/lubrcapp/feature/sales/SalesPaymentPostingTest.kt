package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PostingValidator
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.PaymentId
import com.lubrcapp.lubrcapp.domain.common.PaymentMethodId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.domain.payment.PaymentDirection
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesPaymentPostingTest {

    private val pkr = CurrencyCode.of("PKR")

    private fun payment(amount: String): Payment = Payment(
        id = PaymentId("pay-1"),
        tenantId = TenantId("tenant-1"),
        businessId = BusinessId("business-1"),
        partyId = PartyId("party-1"),
        direction = PaymentDirection.RECEIVED,
        methodId = PaymentMethodId("cash"),
        amount = Money.of(amount, pkr),
        date = LocalDate.of(2026, 10, 24),
        metadata = EntityMetadata.newLocal(
            Instant.parse("2026-10-24T00:00:00Z"),
            DeviceId("device-1"),
        ),
    )

    @Test
    fun `payment posting balances and validates`() {
        val posting = SalesPaymentPosting.paymentToPosting(
            payment("500"),
            "PAY-2026-00001",
            PaymentPostingAccounts("cash-1", "recv-1"),
        )
        assertEquals(Money.of("500", pkr), posting.totals.grandTotal)
        assertEquals(2, posting.journalLines.size)
        assertEquals(Money.of("500", pkr), posting.journalLines[0].debit)
        assertTrue(posting.journalLines[0].credit.isZero)
        assertTrue(posting.journalLines[1].debit.isZero)
        assertEquals(Money.of("500", pkr), posting.journalLines[1].credit)
        assertEquals("pay-1", posting.transactionId.value)
        assertTrue(posting.stockMovements.isEmpty())
        val effect = posting.partyEffect
        assertEquals(PartyBalanceDirection.RECEIVABLE, effect?.direction)
        assertEquals(Money.of("-500", pkr), effect?.amount)
        assertTrue(PostingValidator.validate(posting).isEmpty())
    }
}
