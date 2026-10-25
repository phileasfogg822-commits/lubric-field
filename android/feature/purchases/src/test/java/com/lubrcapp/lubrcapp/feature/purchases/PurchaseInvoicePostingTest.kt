package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PostingValidator
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.PurchaseId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.TransactionLineId
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoice
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoiceLine
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseStatus
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseInvoicePostingTest {

    private val pkr = CurrencyCode.of("PKR")
    private val accounts = PurchasePostingAccounts(
        payableAccountId = "acc-pay",
        expenseAccountId = "acc-exp",
        taxAccountId = "acc-tax",
    )

    private fun invoice() = PurchaseInvoice(
        id = PurchaseId("po-1"),
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        partyId = PartyId("vendor-1"),
        invoiceNumber = "PINV-2026-00001",
        date = LocalDate.of(2026, 10, 25),
        status = PurchaseStatus.DRAFT,
        subtotal = Money.of("10000", pkr),
        taxTotal = Money.of("1700", pkr),
        discount = Money.zero(pkr),
        total = Money.of("11700", pkr),
        paidAmount = Money.zero(pkr),
        metadata = EntityMetadata.newLocal(Instant.parse("2026-10-25T00:00:00Z"), DeviceId("device-a")),
    )

    private fun lines() = listOf(
        PurchaseInvoiceLine(
            id = TransactionLineId("l-1"),
            purchaseId = PurchaseId("po-1"),
            itemId = ItemId("item-1"),
            description = "Engine Oil 4L",
            quantity = Quantity.of(BigDecimal("2")),
            unitPrice = Money.of("5000", pkr),
            taxRate = Percentage.of("17"),
            discount = Money.zero(pkr),
            lineTotal = Money.of("11700", pkr),
        ),
    )

    private fun moneyOf(posting: DocumentPosting, accountId: String, debit: Boolean): BigDecimal {
        val line = posting.journalLines.first { it.accountId.value == accountId }
        return if (debit) line.debit.amount else line.credit.amount
    }

    @Test fun invoicePostingBalancesWithPayableEffectAndValidates() {
        val posting = PurchaseInvoicePosting.invoiceToPosting(invoice(), lines(), accounts)

        assertEquals("PURCHASE_INVOICE", posting.documentType)
        assertEquals("PINV-2026-00001", posting.documentNumber)
        assertEquals("po-1", posting.transactionId.value)
        assertEquals(0, BigDecimal("10000").compareTo(moneyOf(posting, "acc-exp", true)))
        assertEquals(0, BigDecimal("1700").compareTo(moneyOf(posting, "acc-tax", true)))
        assertEquals(0, BigDecimal("11700").compareTo(moneyOf(posting, "acc-pay", false)))
        assertEquals(3, posting.journalLines.size)
        assertTrue(posting.stockMovements.isEmpty())
        val effect = posting.partyEffect!!
        assertEquals(PartyBalanceDirection.PAYABLE, effect.direction)
        assertEquals("vendor-1", effect.partyId.value)
        assertEquals(0, BigDecimal("11700").compareTo(effect.amount.amount))
        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test fun postingWithoutTaxAccountChargesExpenseInFull() {
        val posting = PurchaseInvoicePosting.invoiceToPosting(
            invoice(),
            lines(),
            accounts.copy(taxAccountId = null),
        )

        assertEquals(0, BigDecimal("11700").compareTo(moneyOf(posting, "acc-exp", true)))
        assertEquals(2, posting.journalLines.size)
        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test fun emptyLinesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseInvoicePosting.invoiceToPosting(invoice(), emptyList(), accounts)
        }
    }
}
