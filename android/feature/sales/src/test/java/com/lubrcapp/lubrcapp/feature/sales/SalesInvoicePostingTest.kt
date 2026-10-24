package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.PostingValidator
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesInvoicePostingTest {

    private val pkr = CurrencyCode.of("PKR")
    private val accounts = InvoicePostingAccounts(
        receivableAccountId = "acc-recv",
        revenueAccountId = "acc-rev",
        taxAccountId = "acc-tax",
    )

    private val invoice = SalesDocument(
        id = "inv-1",
        type = SalesDocumentType.INVOICE,
        number = "INV-2026-00001",
        partyId = "party-1",
        date = "2026-10-24",
        status = "SENT",
    )

    private fun lines() = listOf(
        SalesDocumentLine(
            id = "l-1", orderId = "inv-1", position = 0, itemId = "item-1",
            description = "Engine Oil 4L", quantity = BigDecimal("2"), rate = BigDecimal("5000"),
            taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
        ),
        SalesDocumentLine(
            id = "l-2", orderId = "inv-1", position = 1,
            description = "Delivery service", quantity = BigDecimal("1"), rate = BigDecimal("2000"),
        ),
    )

    private fun money(amount: String) = Money.of(BigDecimal(amount), pkr)

    @Test
    fun invoiceBuildsBalancedJournalReceivableAndOutMovements() {
        val posting = SalesInvoicePosting.invoiceToPosting(invoice, lines(), pkr, accounts)
        assertEquals("inv-1", posting.transactionId.value)
        assertEquals("INVOICE", posting.documentType)
        assertEquals("INV-2026-00001", posting.documentNumber)
        assertEquals("party-1", posting.partyId?.value)
        assertEquals(money("13700"), posting.totals.grandTotal)
        assertEquals(money("1700"), posting.totals.taxTotal)

        assertEquals(3, posting.journalLines.size)
        val receivable = posting.journalLines[0]
        assertEquals(AccountId("acc-recv"), receivable.accountId)
        assertEquals(money("13700"), receivable.debit)
        assertTrue(receivable.credit.isZero)
        val revenue = posting.journalLines[1]
        assertEquals(AccountId("acc-rev"), revenue.accountId)
        assertTrue(revenue.debit.isZero)
        assertEquals(money("12000"), revenue.credit)
        val tax = posting.journalLines[2]
        assertEquals(AccountId("acc-tax"), tax.accountId)
        assertEquals(money("1700"), tax.credit)

        assertEquals(1, posting.stockMovements.size)
        val movement = posting.stockMovements[0]
        assertEquals("item-1", movement.itemId.value)
        assertEquals(StockMovementKind.OUT, movement.kind)
        assertEquals(BigDecimal("2"), movement.quantity.amount)
        assertEquals(money("5000"), movement.rate)

        val effect = posting.partyEffect!!
        assertEquals("party-1", effect.partyId.value)
        assertEquals(money("13700"), effect.amount)

        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test
    fun withoutTaxAccountTaxFoldsIntoRevenue() {
        val posting = SalesInvoicePosting.invoiceToPosting(
            invoice,
            lines(),
            pkr,
            accounts.copy(taxAccountId = null),
        )
        assertEquals(2, posting.journalLines.size)
        assertEquals(money("13700"), posting.journalLines[1].credit)
        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test
    fun untaxedInvoiceOmitsTaxLineAndNoPartyOmitsEffect() {
        val untaxed = listOf(
            SalesDocumentLine(
                id = "l-1", orderId = "inv-1", position = 0, itemId = "item-1",
                quantity = BigDecimal("1"), rate = BigDecimal("3000"),
            ),
        )
        val posting = SalesInvoicePosting.invoiceToPosting(invoice, untaxed, pkr, accounts)
        assertEquals(2, posting.journalLines.size)
        assertTrue(PostingValidator.validate(posting).isEmpty())

        val noParty = SalesInvoicePosting.invoiceToPosting(
            invoice.copy(partyId = null),
            untaxed,
            pkr,
            accounts,
        )
        assertNull(noParty.partyId)
        assertNull(noParty.partyEffect)
        assertTrue(PostingValidator.validate(noParty).isEmpty())
    }

    @Test
    fun zeroTotalInvoicePostsEmptyJournal() {
        val zero = listOf(
            SalesDocumentLine(
                id = "l-1", orderId = "inv-1", position = 0, itemId = "item-1",
                quantity = BigDecimal("1"), rate = BigDecimal("0"),
            ),
        )
        val posting = SalesInvoicePosting.invoiceToPosting(invoice, zero, pkr, accounts)
        assertTrue(posting.totals.grandTotal.isZero)
        assertTrue(posting.journalLines.isEmpty())
        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test
    fun builderRefusesNonInvoiceEmptyAndNegativeLines() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesInvoicePosting.invoiceToPosting(
                invoice.copy(type = SalesDocumentType.SALES_ORDER),
                lines(),
                pkr,
                accounts,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesInvoicePosting.invoiceToPosting(invoice, emptyList(), pkr, accounts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val negative = listOf(
                SalesDocumentLine(
                    id = "l-1", orderId = "inv-1", position = 0, itemId = "item-1",
                    quantity = BigDecimal("-1"), rate = BigDecimal("100"),
                ),
            )
            SalesInvoicePosting.invoiceToPosting(invoice, negative, pkr, accounts)
        }
    }
}
