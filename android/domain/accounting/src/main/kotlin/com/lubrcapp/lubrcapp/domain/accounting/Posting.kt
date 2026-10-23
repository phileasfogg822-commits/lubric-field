package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import com.lubrcapp.lubrcapp.domain.common.WarehouseId

/**
 * The posting contract: what a POSTED document must emit, as data.
 *
 * Phase 17 defines the contract only — no ledger is implemented here.
 * Later phases consume it exactly as named:
 * - [StockMovement]s are what Phase 19's inventory ledger records
 *   (table "stock_movements").
 * - [PostingJournalLine]s are what Phase 20's accounting core turns
 *   into a journal entry (tables "account_transactions").
 * - [PartyLedgerEffect] is what Phase 22's customer ledger applies
 *   (table "party_ledger").
 *
 * The types are deliberately permissive (no init-time throwing beyond
 * identity basics): [PostingValidator] is the gate, and it reports
 * every violation at once instead of failing on the first.
 */
enum class StockMovementKind {
    IN,
    OUT,
}

data class StockMovement(
    val itemId: ItemId,
    val warehouseId: WarehouseId? = null,
    val kind: StockMovementKind,
    /** Always a positive quantity; direction is carried by [kind]. */
    val quantity: Quantity,
    val rate: Money? = null,
    val batchId: String? = null,
)

data class PostingJournalLine(
    val accountId: AccountId,
    val debit: Money,
    val credit: Money,
)

enum class PartyBalanceDirection {
    RECEIVABLE,
    PAYABLE,
}

data class PartyLedgerEffect(
    val partyId: PartyId,
    val direction: PartyBalanceDirection,
    /** Signed change: positive increases the balance in [direction]. */
    val amount: Money,
)

data class DocumentPosting(
    val transactionId: TransactionId,
    val documentType: String,
    val documentNumber: String,
    val partyId: PartyId? = null,
    val totals: DocumentTotals,
    val stockMovements: List<StockMovement> = emptyList(),
    val journalLines: List<PostingJournalLine> = emptyList(),
    val partyEffect: PartyLedgerEffect? = null,
)
