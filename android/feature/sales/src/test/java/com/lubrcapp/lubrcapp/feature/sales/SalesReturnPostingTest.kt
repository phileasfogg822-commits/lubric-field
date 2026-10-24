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

class SalesReturnPostingTest {

    private val pkr = CurrencyCode.of("PKR")
    private val accounts = InvoicePostingAccounts(
        receivableAccountId = "acc-recv",
        revenueAccountId = "acc-rev",
        taxAccountId = "acc-tax",
    )

    private val returnDocument = SalesDocument(
        id = "ret-1",
        type = SalesDocumentType.SALES_RETURN,
        number = "SR-2026-00001",
        partyId = "party-1",
        date = "2026-10-24",
        status = "SENT",
        convertedFromId = "inv-1",
    )

    private fun lines() = listOf(
        SalesDocumentLine(
            id = "l-1", orderId = "ret-1", position = 0, itemId = "item-1",
            description = "Engine Oil 4L", quantity = BigDecimal("2"), rate = BigDecimal("5000"),
            taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
        ),
        SalesDocumentLine(
            id = "l-2", orderId = "ret-1", position = 1,
            description = "Delivery service", quantity = BigDecimal("1"), rate = BigDecimal("2000"),
        ),
    )

    private fun money(amount: String) = Money.of(BigDecimal(amount), pkr)

    @Test
    fun returnBuildsSwappedJournalAndInMovements() {
        val posting = SalesReturnPosting.returnToPosting(returnDocument, lines(), pkr, accounts)
        assertEquals("ret-1", posting.transactionId.value)
        assertEquals("SALES_RETURN", posting.documentType)
        assertEquals("SR-2026-00001", posting.documentNumber)
        assertEquals(money("13700"), posting.totals.grandTotal)

        // Sides swapped versus the invoice: revenue and tax are
        // debits now, the receivable is the credit.
        assertEquals(3, posting.journalLines.size)
        val revenue = posting.journalLines[0]
        assertEquals(AccountId("acc-rev"), revenue.accountId)
        assertEquals(money("12000"), revenue.debit)
        assertTrue(revenue.credit.isZero)
        val tax = posting.journalLines[1]
        assertEquals(AccountId("acc-tax"), tax.accountId)
        assertEquals(money("1700"), tax.debit)
        val receivable = posting.journalLines[2]
        assertEquals(AccountId("acc-recv"), receivable.accountId)
        assertEquals(money("13700"), receivable.credit)

        assertEquals(1, posting.stockMovements.size)
        val movement = posting.stockMovements[0]
        assertEquals("item-1", movement.itemId.value)
        assertEquals(StockMovementKind.IN, movement.kind)
        assertEquals(BigDecimal("2"), movement.quantity.amount)

        // The receivable falls by the returned total.
        val effect = posting.partyEffect!!
        assertEquals("party-1", effect.partyId.value)
        assertEquals(money("-13700"), effect.amount)

        assertTrue(PostingValidator.validate(posting).isEmpty())
    }

    @Test
    fun creditNotePostsJournalOnlyAndWithoutTaxAccountTaxFoldsIn() {
        val note = returnDocument.copy(id = "cn-1", type = SalesDocumentType.CREDIT_NOTE, number = "CN-2026-00001")
        val posting = SalesReturnPosting.creditNoteToPosting(note, lines(), pkr, accounts)
        assertTrue(posting.stockMovements.isEmpty())
        assertEquals(3, posting.journalLines.size)
        assertTrue(PostingValidator.validate(posting).isEmpty())

        val noTax = SalesReturnPosting.returnToPosting(
            returnDocument,
            lines(),
            pkr,
            accounts.copy(taxAccountId = null),
        )
        assertEquals(2, noTax.journalLines.size)
        assertEquals(money("13700"), noTax.journalLines[0].debit)
        assertTrue(PostingValidator.validate(noTax).isEmpty())
    }

    @Test
    fun zeroTotalPostsEmptyJournalAndNoPartyOmitsEffect() {
        val zero = listOf(
            SalesDocumentLine(
                id = "l-1", orderId = "ret-1", position = 0, itemId = "item-1",
                quantity = BigDecimal("1"), rate = BigDecimal("0"),
            ),
        )
        val posting = SalesReturnPosting.returnToPosting(returnDocument, zero, pkr, accounts)
        assertTrue(posting.journalLines.isEmpty())
        assertTrue(PostingValidator.validate(posting).isEmpty())

        val noParty = SalesReturnPosting.returnToPosting(
            returnDocument.copy(partyId = null),
            zero,
            pkr,
            accounts,
        )
        assertNull(noParty.partyId)
        assertNull(noParty.partyEffect)
        assertTrue(PostingValidator.validate(noParty).isEmpty())
    }

    @Test
    fun builderRefusesWrongTypeEmptyAndNegativeLines() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesReturnPosting.returnToPosting(
                returnDocument.copy(type = SalesDocumentType.CREDIT_NOTE),
                lines(),
                pkr,
                accounts,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesReturnPosting.creditNoteToPosting(
                returnDocument,
                lines(),
                pkr,
                accounts,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesReturnPosting.returnToPosting(returnDocument, emptyList(), pkr, accounts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val negative = listOf(
                SalesDocumentLine(
                    id = "l-1", orderId = "ret-1", position = 0, itemId = "item-1",
                    quantity = BigDecimal("-1"), rate = BigDecimal("100"),
                ),
            )
            SalesReturnPosting.returnToPosting(returnDocument, negative, pkr, accounts)
        }
    }
}
