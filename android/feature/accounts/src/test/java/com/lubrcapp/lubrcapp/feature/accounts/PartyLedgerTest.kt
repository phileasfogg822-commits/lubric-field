package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineTotals
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PartyLedgerEffect
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyLedgerTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val pkr = CurrencyCode.of("PKR")
    private val usd = CurrencyCode.of("USD")

    private class TableAwareFake : OperationalBusinessRepository {
        val rows = mutableListOf<Pair<String, OperationalRecord>>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(rows.filter { it.first == table }.map { it.second })

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            rows.filter { it.first == table }.map { it.second }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            rows.removeAll { it.first == table && it.second.id == id }
            rows.add(table to OperationalRecord(id, json))
        }
    }

    private fun money(amount: String): Money = Money.of(amount, pkr)

    private fun salePosting(effect: PartyLedgerEffect?): DocumentPosting {
        val line = DocumentLineTotals(
            grossAmount = money("100000"),
            discountAmount = money("0"),
            taxableBase = money("100000"),
            taxComponents = emptyList(),
            totalTax = money("0"),
            lineTotal = money("100000"),
        )
        return DocumentPosting(
            transactionId = TransactionId("tx-1"),
            documentType = "INVOICE",
            documentNumber = "INV-2026-00001",
            // The posting's party must match the effect's party:
            // the validator rejects an effect without a party
            // (PARTY_EFFECT_MISMATCH), exactly as real 21.3
            // postings carry both from the invoice.
            partyId = effect?.partyId,
            totals = DocumentTotals(
                lines = listOf(line),
                charges = emptyList(),
                grossBeforeDiscounts = money("100000"),
                lineDiscountsTotal = money("0"),
                taxableBaseTotal = money("100000"),
                taxComponents = emptyList(),
                taxTotal = money("0"),
                chargesTotal = money("0"),
                invoiceDiscountAmount = money("0"),
                roundOffAmount = money("0"),
                grandTotal = money("100000"),
            ),
            journalLines = listOf(
                PostingJournalLine(AccountId("receivable"), money("100000"), Money.zero(pkr)),
                PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("100000")),
            ),
            partyEffect = effect,
        )
    }

    private fun row(
        id: String,
        partyId: String = "party-1",
        direction: String = "RECEIVABLE",
        amount: String = "0",
        currency: String = "PKR",
    ): PartyLedgerRecord = PartyLedgerRecord(
        id = id,
        entryId = id,
        partyId = partyId,
        direction = direction,
        amount = BigDecimal(amount),
        currency = currency,
        transactionId = "tx-$id",
        documentType = "INVOICE",
        documentNumber = "INV-2026-00001",
        date = "2026-10-24",
        description = "INVOICE INV-2026-00001",
        createdAt = "2026-10-24T00:00:00Z",
    )

    @Test
    fun ledgerCodecRoundTrips() {
        val record = row("je-tx-1", amount = "100000")
        assertEquals(record, PartyLedgerJson.decode(record.id, PartyLedgerJson.encode(record)))
    }

    @Test
    fun ledgerCodecToleratesGarbage() {
        val decoded = PartyLedgerJson.decode("row-9", "{broken")
        assertEquals("row-9", decoded.id)
        assertEquals(BigDecimal.ZERO, decoded.amount)
    }

    @Test
    fun fromPostingMapsEffectAndIds() {
        val entry = JournalEntryRecord(
            id = "je-tx-1",
            date = "2026-10-24",
            description = "INVOICE INV-2026-00001",
            referenceNumber = "INV-2026-00001",
            lines = emptyList(),
            createdAt = "2026-10-24T00:00:00Z",
        )
        val effect = PartyLedgerEffect(
            PartyId("party-1"),
            PartyBalanceDirection.RECEIVABLE,
            money("100000"),
        )
        val mapped = PartyLedgerRecord.fromPosting(salePosting(effect), entry)
        assertEquals("je-tx-1", mapped?.id)
        assertEquals("je-tx-1", mapped?.entryId)
        assertEquals("party-1", mapped?.partyId)
        assertEquals("RECEIVABLE", mapped?.direction)
        // CompareTo, not equals: Money normalizes 100000 to
        // 1E+5, and raw BigDecimal.equals is scale-sensitive
        // (the project's standing trap).
        assertEquals(0, BigDecimal("100000").compareTo(mapped?.amount))
        assertEquals("PKR", mapped?.currency)
        assertEquals("tx-1", mapped?.transactionId)
    }

    @Test
    fun fromPostingReturnsNullWithoutEffect() {
        val entry = JournalEntryRecord(
            id = "je-tx-1",
            date = "2026-10-24",
            description = "INVOICE INV-2026-00001",
            lines = emptyList(),
            createdAt = "2026-10-24T00:00:00Z",
        )
        assertNull(PartyLedgerRecord.fromPosting(salePosting(null), entry))
    }

    @Test
    fun receivableBalanceNetsSignedAmounts() {
        val rows = listOf(
            row("je-1", amount = "100000"),
            row("je-2", amount = "-30000"),
            row("je-3", partyId = "party-2", amount = "99999"),
            row("je-4", currency = "USD", amount = "88888"),
            row("je-5", direction = "UNKNOWN", amount = "77777"),
        )
        assertEquals(money("70000"), PartyLedgerBalance.receivableBalance(rows, "party-1", pkr))
        assertEquals(Money.of("0", pkr), PartyLedgerBalance.receivableBalance(rows, "party-9", pkr))
    }

    @Test
    fun negativeNetIsCreditBalanceNotClamped() {
        val rows = listOf(
            row("je-1", amount = "10000"),
            row("je-2", amount = "-30000"),
        )
        assertEquals(money("-20000"), PartyLedgerBalance.receivableBalance(rows, "party-1", pkr))
    }

    @Test
    fun payableBalanceSumsPayableOnly() {
        val rows = listOf(
            row("je-1", direction = "PAYABLE", amount = "50000"),
            row("je-2", amount = "100000"),
        )
        assertEquals(money("50000"), PartyLedgerBalance.payableBalance(rows, "party-1", pkr))
        assertEquals(Money.of("0", usd), PartyLedgerBalance.payableBalance(rows, "party-1", usd))
    }

    @Test
    fun postDocumentSavesLedgerRow() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val effect = PartyLedgerEffect(
            PartyId("party-1"),
            PartyBalanceDirection.RECEIVABLE,
            money("100000"),
        )
        val result = vm.postDocument(
            scope = scope,
            posting = salePosting(effect),
            date = LocalDate.parse("2026-10-24"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
            deviceId = DeviceId("device-a"),
        )
        assertTrue(result is AccountsViewModel.DocumentPostResult.Posted)
        val rows = vm.loadLedgerRowsForParty(scope, "party-1")
        assertEquals(1, rows.size)
        assertEquals("je-tx-1", rows[0].id)
        assertEquals(BigDecimal("100000"), rows[0].amount)
        assertEquals("tx-1", rows[0].transactionId)
    }

    @Test
    fun repostOverwritesSameLedgerRow() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val effect = PartyLedgerEffect(
            PartyId("party-1"),
            PartyBalanceDirection.RECEIVABLE,
            money("100000"),
        )
        val posting = salePosting(effect)
        val date = LocalDate.parse("2026-10-24")
        val now = Instant.parse("2026-10-24T00:00:00Z")
        vm.postDocument(scope, posting, date, now = now, deviceId = DeviceId("device-a"))
        vm.postDocument(scope, posting, date, now = now, deviceId = DeviceId("device-a"))
        assertEquals(1, vm.loadLedgerRowsForParty(scope, "party-1").size)
    }

    @Test
    fun rejectedPostingSavesNoLedgerRow() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val effect = PartyLedgerEffect(
            PartyId("party-1"),
            PartyBalanceDirection.RECEIVABLE,
            money("100000"),
        )
        val unbalanced = salePosting(effect).copy(
            journalLines = listOf(
                PostingJournalLine(AccountId("receivable"), money("100000"), Money.zero(pkr)),
                PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("99999")),
            ),
        )
        val result = vm.postDocument(
            scope = scope,
            posting = unbalanced,
            date = LocalDate.parse("2026-10-24"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
            deviceId = DeviceId("device-a"),
        )
        assertTrue(result is AccountsViewModel.DocumentPostResult.Rejected)
        assertEquals(0, vm.loadLedgerRowsForParty(scope, "party-1").size)
    }

    @Test
    fun postingWithoutEffectSavesNoLedgerRow() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val result = vm.postDocument(
            scope = scope,
            posting = salePosting(null),
            date = LocalDate.parse("2026-10-24"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
            deviceId = DeviceId("device-a"),
        )
        assertTrue(result is AccountsViewModel.DocumentPostResult.Posted)
        assertEquals(0, vm.loadLedgerRowsForParty(scope, "party-1").size)
    }
}
