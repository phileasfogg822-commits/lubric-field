package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PostingValidator
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseDebitNotePostingTest {

    private val pkr = CurrencyCode.of("PKR")
    private val accounts = DebitNotePostingAccounts(
        payableAccountId = "acc-pay",
        expenseAccountId = "acc-exp",
        taxAccountId = "acc-tax",
    )

    private fun lines() = listOf(
        DebitNoteLine(
            id = "d-1",
            itemId = "item-1",
            description = "Engine Oil 4L",
            quantity = Quantity.of(BigDecimal("2")),
            unitPrice = Money.of("5000", pkr),
            taxRate = Percentage.of("17"),
            discount = Money.zero(pkr),
        ),
    )

    private fun posting() = PurchaseDebitNotePosting.debitNoteToPosting(
        debitNoteId = "dn-1",
        documentNumber = "DN-2026-00001",
        partyId = PartyId("vendor-1"),
        lines = lines(),
        accounts = accounts,
    )

    @Test fun debitNotePostingReversesWithNegativePayableEffectAndValidates() {
        val result = posting()

        assertEquals("DEBIT_NOTE", result.documentType)
        assertEquals("DN-2026-00001", result.documentNumber)
        assertEquals("dn-1", result.transactionId.value)
        val payable = result.journalLines.first { it.accountId.value == "acc-pay" }
        val expense = result.journalLines.first { it.accountId.value == "acc-exp" }
        val tax = result.journalLines.first { it.accountId.value == "acc-tax" }
        assertEquals(0, BigDecimal("11700").compareTo(payable.debit.amount))
        assertEquals(0, BigDecimal("10000").compareTo(expense.credit.amount))
        assertEquals(0, BigDecimal("1700").compareTo(tax.credit.amount))
        assertEquals(3, result.journalLines.size)
        val effect = result.partyEffect!!
        assertEquals(PartyBalanceDirection.PAYABLE, effect.direction)
        assertEquals(0, BigDecimal("-11700").compareTo(effect.amount.amount))
        assertEquals(1, result.stockMovements.size)
        assertEquals(StockMovementKind.OUT, result.stockMovements[0].kind)
        assertEquals(0, BigDecimal("2").compareTo(result.stockMovements[0].quantity.amount))
        assertTrue(PostingValidator.validate(result).isEmpty())
    }

    @Test fun postingWithoutTaxAccountCreditsExpenseInFull() {
        val result = PurchaseDebitNotePosting.debitNoteToPosting(
            debitNoteId = "dn-1",
            documentNumber = "DN-2026-00001",
            partyId = PartyId("vendor-1"),
            lines = lines(),
            accounts = accounts.copy(taxAccountId = null),
        )

        assertEquals(0, BigDecimal("11700").compareTo(
            result.journalLines.first { it.accountId.value == "acc-exp" }.credit.amount,
        ))
        assertEquals(2, result.journalLines.size)
        assertTrue(PostingValidator.validate(result).isEmpty())
    }

    @Test fun zeroQuantityLineEmitsNoMovement() {
        val result = PurchaseDebitNotePosting.debitNoteToPosting(
            debitNoteId = "dn-1",
            documentNumber = "DN-2026-00001",
            partyId = PartyId("vendor-1"),
            lines = lines() + DebitNoteLine(
                id = "d-2",
                itemId = "item-2",
                quantity = Quantity.of(BigDecimal("0")),
                unitPrice = Money.of("1000", pkr),
                discount = Money.zero(pkr),
            ),
            accounts = accounts,
        )

        assertEquals(1, result.stockMovements.size)
        assertTrue(PostingValidator.validate(result).isEmpty())
    }

    @Test fun emptyLinesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseDebitNotePosting.debitNoteToPosting(
                debitNoteId = "dn-1",
                documentNumber = "DN-2026-00001",
                partyId = PartyId("vendor-1"),
                lines = emptyList(),
                accounts = accounts,
            )
        }
    }

    @Test fun negativeQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseDebitNotePosting.debitNoteToPosting(
                debitNoteId = "dn-1",
                documentNumber = "DN-2026-00001",
                partyId = PartyId("vendor-1"),
                lines = listOf(
                    DebitNoteLine(
                        id = "d-1",
                        itemId = "item-1",
                        quantity = Quantity.of(BigDecimal("-1")),
                        unitPrice = Money.of("5000", pkr),
                        discount = Money.zero(pkr),
                    ),
                ),
                accounts = accounts,
            )
        }
    }
}
