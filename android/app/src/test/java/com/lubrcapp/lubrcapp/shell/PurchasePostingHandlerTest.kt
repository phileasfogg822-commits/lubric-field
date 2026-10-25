package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
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
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.inventory.MovementType
import com.lubrcapp.lubrcapp.feature.purchases.DebitNoteLine
import com.lubrcapp.lubrcapp.feature.purchases.DebitNotePostingAccounts
import com.lubrcapp.lubrcapp.feature.purchases.PurchasePostResult
import com.lubrcapp.lubrcapp.feature.purchases.PurchasePostingAccounts
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasePostingHandlerTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val pkr = CurrencyCode.of("PKR")
    private val invoiceAccounts = PurchasePostingAccounts(
        payableAccountId = "acc-pay",
        expenseAccountId = "acc-exp",
        taxAccountId = "acc-tax",
    )
    private val debitNoteAccounts = DebitNotePostingAccounts(
        payableAccountId = "acc-pay",
        expenseAccountId = "acc-exp",
        taxAccountId = "acc-tax",
    )

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(tables[table]?.toList() ?: emptyList())
        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            tables[table]?.toList() ?: emptyList()
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            val rows = tables.getOrPut(table) { mutableListOf() }
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    private class Harness(fake: FakeRepository) {
        val purchases = PurchasesViewModel(fake)
        val accountsVm = AccountsViewModel(fake)
        val inventory = InventoryViewModel(fake)
    }

    private fun invoice() = PurchaseInvoice(
        id = PurchaseId("po-1"),
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        partyId = PartyId("vendor-1"),
        invoiceNumber = "PINV-2026-00001",
        date = LocalDate.of(2026, 10, 25),
        status = PurchaseStatus.RECEIVED,
        subtotal = Money.of("10000", pkr),
        taxTotal = Money.of("1700", pkr),
        discount = Money.zero(pkr),
        total = Money.of("11700", pkr),
        paidAmount = Money.zero(pkr),
        metadata = EntityMetadata.newLocal(Instant.parse("2026-10-25T00:00:00Z"), DeviceId("device-a")),
    )

    private fun invoiceLines() = listOf(
        PurchaseInvoiceLine(
            id = TransactionLineId("l-1"),
            purchaseId = PurchaseId("po-1"),
            itemId = ItemId("item-1"),
            quantity = Quantity.of(BigDecimal("2")),
            unitPrice = Money.of("5000", pkr),
            taxRate = Percentage.of("17"),
            discount = Money.zero(pkr),
            lineTotal = Money.of("11700", pkr),
        ),
    )

    private fun debitNoteLines() = listOf(
        DebitNoteLine(
            id = "d-1",
            itemId = "item-1",
            quantity = Quantity.of(BigDecimal("2")),
            unitPrice = Money.of("5000", pkr),
            taxRate = Percentage.of("17"),
            discount = Money.zero(pkr),
        ),
    )

    @Test fun postingAnInvoiceWritesJournalPayableAndLedgerRow() = runBlocking {
        val h = Harness(FakeRepository())
        val result = PurchasePostingHandler.postInvoice(
            scope, invoice(), invoiceLines(), invoiceAccounts, h.purchases, h.accountsVm,
        )

        assertTrue(result is PurchasePostResult.Posted)
        assertEquals("PINV-2026-00001", (result as PurchasePostResult.Posted).documentNumber)
        val entries = h.accountsVm.loadJournalEntries(scope)
        assertEquals(1, entries.size)
        assertEquals("je-po-1", entries[0].id)
        val payable = h.purchases.loadPayableForPurchase(scope, "po-1")!!
        assertEquals(0, BigDecimal("11700").compareTo(payable.totalAmount))
        assertEquals(0, BigDecimal("0").compareTo(payable.paidAmount))
        assertEquals("RECEIVED", payable.status)
        val rows = h.accountsVm.loadLedgerRowsForParty(scope, "vendor-1")
        assertEquals(1, rows.size)
        assertEquals("PAYABLE", rows[0].direction)
        assertEquals(0, BigDecimal("11700").compareTo(rows[0].amount))
    }

    @Test fun repostingAnInvoiceDoesNotDuplicateJournalOrPayable() = runBlocking {
        val h = Harness(FakeRepository())
        PurchasePostingHandler.postInvoice(scope, invoice(), invoiceLines(), invoiceAccounts, h.purchases, h.accountsVm)
        PurchasePostingHandler.postInvoice(scope, invoice(), invoiceLines(), invoiceAccounts, h.purchases, h.accountsVm)

        assertEquals(1, h.accountsVm.loadJournalEntries(scope).size)
        assertEquals(1, h.purchases.loadPayables(scope).size)
        assertEquals(1, h.accountsVm.loadLedgerRowsForParty(scope, "vendor-1").size)
    }

    @Test fun postingADebitNoteWritesNegativeLedgerRowAndOutMovement() = runBlocking {
        val h = Harness(FakeRepository())
        val result = PurchasePostingHandler.postDebitNote(
            scope, "dn-1", "DN-2026-00001", PartyId("vendor-1"), debitNoteLines(),
            debitNoteAccounts, LocalDate.of(2026, 10, 25), h.accountsVm, h.inventory,
        )

        assertTrue(result is PurchasePostResult.Posted)
        val entries = h.accountsVm.loadJournalEntries(scope)
        assertEquals(1, entries.size)
        assertEquals("je-dn-1", entries[0].id)
        val rows = h.accountsVm.loadLedgerRowsForParty(scope, "vendor-1")
        assertEquals(1, rows.size)
        assertEquals("PAYABLE", rows[0].direction)
        assertEquals(0, BigDecimal("-11700").compareTo(rows[0].amount))
        val movements = h.inventory.loadMovements(scope)
        assertEquals(1, movements.size)
        assertEquals(MovementType.PURCHASE, movements[0].type)
        assertEquals(0, BigDecimal("-2").compareTo(movements[0].quantity))
        assertEquals("dn-1", movements[0].referenceId)
    }

    @Test fun repostingADebitNoteDoesNotDuplicateMovements() = runBlocking {
        val h = Harness(FakeRepository())
        repeat(2) {
            PurchasePostingHandler.postDebitNote(
                scope, "dn-1", "DN-2026-00001", PartyId("vendor-1"), debitNoteLines(),
                debitNoteAccounts, LocalDate.of(2026, 10, 25), h.accountsVm, h.inventory,
            )
        }

        assertEquals(1, h.inventory.loadMovements(scope).size)
        assertEquals(1, h.accountsVm.loadJournalEntries(scope).size)
    }
}
