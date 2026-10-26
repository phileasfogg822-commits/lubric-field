package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldCollectionTest {

    private val pkr = CurrencyCode.of("PKR")
    private val party = PartyId("party-1")
    private val otherParty = PartyId("party-2")

    private fun collection(amount: String): FieldCollection = FieldCollection(
        id = "col-1",
        partyId = party,
        amount = Money.of(amount, pkr),
        mode = CollectionMode.CASH,
        reference = "ref-1",
        receiptNumber = "RC-1",
        date = "2026-10-27",
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
    fun validatePassesOnPositiveAmount() {
        FieldCollectionOps.validate(collection("500"))
    }

    @Test
    fun validateThrowsOnZeroAndNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            FieldCollectionOps.validate(collection("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FieldCollectionOps.validate(collection("-100"))
        }
    }

    @Test
    fun allocateAppliesOldestFirstAcrossInvoices() {
        val invoices = listOf(
            openInvoice("inv-2", "800", due = LocalDate.of(2026, 10, 22)),
            openInvoice("inv-1", "500", due = LocalDate.of(2026, 10, 20)),
        )
        val result = FieldCollectionOps.allocate(collection("1000"), invoices)
        assertEquals(listOf("inv-1", "inv-2"), result.applications.map { it.invoiceId.value })
        assertEquals(Money.of("500", pkr), result.applications[0].applied)
        assertEquals(Money.of("500", pkr), result.applications[1].applied)
        assertTrue(result.unallocated.isZero)
    }

    @Test
    fun allocateSkipsOtherPartyAndNonCollectible() {
        val invoices = listOf(
            openInvoice("inv-x", "500", partyId = otherParty),
            openInvoice("inv-y", "500", collectible = false),
            openInvoice("inv-1", "500"),
        )
        val result = FieldCollectionOps.allocate(collection("500"), invoices)
        assertEquals(listOf("inv-1"), result.applications.map { it.invoiceId.value })
    }

    @Test
    fun allocateSkipsCurrencyMismatch() {
        val usd = CurrencyCode.of("USD")
        val invoices = listOf(
            openInvoice("inv-usd", "500", currency = usd),
            openInvoice("inv-1", "500"),
        )
        val result = FieldCollectionOps.allocate(collection("500"), invoices)
        assertEquals(listOf("inv-1"), result.applications.map { it.invoiceId.value })
    }

    @Test
    fun allocateLeavesUnallocatedLeftover() {
        val result = FieldCollectionOps.allocate(
            collection("1000"),
            listOf(openInvoice("inv-1", "400")),
        )
        assertEquals(Money.of("600", pkr), result.unallocated)
    }

    @Test
    fun allocateClampsAtOutstandingMinusAlreadyPaid() {
        val result = FieldCollectionOps.allocate(
            collection("1000"),
            listOf(openInvoice("inv-1", "500", alreadyPaid = "200")),
        )
        assertEquals(Money.of("300", pkr), result.applications[0].applied)
        assertEquals(Money.of("700", pkr), result.unallocated)
    }

    @Test
    fun recordKeepsModeReferenceReceiptAndDate() {
        val collected = collection("500")
        assertEquals(CollectionMode.CASH, collected.mode)
        assertEquals("ref-1", collected.reference)
        assertEquals("RC-1", collected.receiptNumber)
        assertEquals("2026-10-27", collected.date)
    }
}
