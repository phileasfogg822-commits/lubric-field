package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.domain.payment.PaymentDirection
import com.lubrcapp.lubrcapp.domain.payment.PaymentId
import com.lubrcapp.lubrcapp.domain.payment.PaymentMethodId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasePaymentPostingTest {

    @Test
    fun paymentPostsDebitPayableCreditCashWithNegativePayableEffect() {
        val payment = Payment(
            id = PaymentId("pay-1"),
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-a"),
            partyId = PartyId("vendor-1"),
            direction = PaymentDirection.PAID,
            methodId = PaymentMethodId("CASH"),
            amount = Money.of(BigDecimal("20000"), CurrencyCode("PKR")),
            relatedInvoiceId = null,
            date = LocalDate.parse("2026-10-28"),
            metadata = EntityMetadata.newLocal(Instant.now(), DeviceId("device-a")),
        )
        val posting = PurchasePaymentPosting.paymentToPosting(
            payment,
            "PINV-2026-00001",
            PurchasePaymentPostingAccounts(cashAccountId = "cash-1", payableAccountId = "pay-1"),
        )
        assertEquals("pay-1", posting.transactionId.value)
        assertEquals("PAYMENT", posting.documentType)
        assertEquals("PINV-2026-00001", posting.documentNumber)
        assertEquals(0, posting.totals.grandTotal.amount.compareTo(BigDecimal("20000")))
        assertTrue(posting.stockMovements.isEmpty())
        assertEquals(2, posting.journalLines.size)
        val debit = posting.journalLines[0]
        assertEquals("pay-1", debit.accountId.value)
        assertEquals(0, debit.debit.amount.compareTo(BigDecimal("20000")))
        assertEquals(0, debit.credit.amount.compareTo(BigDecimal.ZERO))
        val credit = posting.journalLines[1]
        assertEquals("cash-1", credit.accountId.value)
        assertEquals(0, credit.debit.amount.compareTo(BigDecimal.ZERO))
        assertEquals(0, credit.credit.amount.compareTo(BigDecimal("20000")))
        val effect = posting.partyEffect
        assertEquals(PartyBalanceDirection.PAYABLE, effect?.direction)
        assertEquals(0, effect?.amount?.amount?.compareTo(BigDecimal("-20000")))
    }
}
