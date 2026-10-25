package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoice
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoiceLine
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.inventory.MovementType
import com.lubrcapp.lubrcapp.feature.inventory.StockMovementRecord
import com.lubrcapp.lubrcapp.feature.purchases.DebitNoteLine
import com.lubrcapp.lubrcapp.feature.purchases.DebitNotePostingAccounts
import com.lubrcapp.lubrcapp.feature.purchases.PurchaseDebitNotePosting
import com.lubrcapp.lubrcapp.feature.purchases.PurchaseInvoicePosting
import com.lubrcapp.lubrcapp.feature.purchases.PurchasePostResult
import com.lubrcapp.lubrcapp.feature.purchases.PurchasePostingAccounts
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesViewModel
import com.lubrcapp.lubrcapp.feature.purchases.VendorPayableRecord
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 23.6 (STAGED — integrates after 23.5 accepts) —
 * the app-layer purchases posting join, the mirror of
 * 21.5's SalesPostingHandler. This is the one place
 * that may see purchases, accounts, and inventory
 * ViewModels at once: feature modules do not depend
 * on each other, so the join cannot live in
 * feature/purchases — it lives here, in the shell,
 * which already depends on every feature.
 *
 * The sequence, and its order is the whole point:
 * 1. Build the posting with the 23.2/23.5 builders.
 * 2. Post through AccountsViewModel.postDocument
 *    (20.2 + 22.3): the journal entry, the
 *    account_transactions rows, and the party_ledger
 *    row from the posting's effect land together —
 *    including the NEGATIVE PAYABLE effect of a
 *    debit note, which is how the ledger learns a
 *    return reduced what is owed.
 * 3. ONLY on Posted: for invoices, save the payable
 *    record (23.1) — id = invoice id, total = the
 *    posting's grand total, paid seeded from the
 *    invoice's derived paidAmount. For debit notes,
 *    persist the stock OUT movements through the
 *    inventory ViewModel (the 19.1 ledger
 *    convention) and change NO payable record: the
 *    ledger is the truth for the reduced balance,
 *    and the record's total stays the invoice total.
 *    A rejected posting moves nothing anywhere.
 *
 * Differences from 21.5, all structural:
 * - The join TAKES the invoice and lines as
 *   arguments: feature/purchases has no typed
 *   document/lines persistence to load them through
 *   (its ViewModel is generic payloads over
 *   "purchase_orders"). A typed purchase-order
 *   record is future work, not this package.
 * - Invoice postings move no stock: receiving owns
 *   stock (23.3), and its movements are persisted
 *   by the receiving flow, not here. Only debit
 *   notes move stock through this join, OUT.
 * - Debit-note OUT rows use type PURCHASE with a
 *   NEGATED quantity: a debit note is a purchase
 *   reversal, and the movement enum has no
 *   debit-note type — the negated PURCHASE row is
 *   the mirror of 21.5's SALE row, and the sign
 *   convention (positive adds, negative removes)
 *   is what the ledger math reads.
 * - No serial handling: debit-note lines carry no
 *   serials, so there is nothing to flip.
 * - No creditCheck: the validator's credit check
 *   fires only for positive RECEIVABLE effects, and
 *   vendor postings carry PAYABLE effects.
 * - Posting does not change any document status:
 *   status moves through receiving and payment
 *   flows (23.3, 23.4), not through the journal.
 *
 * Retry safety: the journal overwrites by derived
 * id ("je-<transactionId>"), the payable overwrites
 * by invoice id (a mutable table), and movements
 * are skipped when this document already has rows —
 * so re-posting after a mid-sequence failure cannot
 * double-count stock or double-book the payable.
 */
object PurchasePostingHandler {

    suspend fun postInvoice(
        scope: OperationalScope,
        invoice: PurchaseInvoice,
        lines: List<PurchaseInvoiceLine>,
        accounts: PurchasePostingAccounts,
        purchasesViewModel: PurchasesViewModel,
        accountsViewModel: AccountsViewModel,
    ): PurchasePostResult {
        val posting = PurchaseInvoicePosting.invoiceToPosting(invoice, lines, accounts)
        val now = Instant.now()
        val result = accountsViewModel.postDocument(
            scope = scope,
            posting = posting,
            date = invoice.date,
            now = now,
            deviceId = DeviceId(scope.deviceId),
        )
        if (result is AccountsViewModel.DocumentPostResult.Rejected) {
            return PurchasePostResult.Rejected(result.violations.map { it.name })
        }
        purchasesViewModel.savePayable(
            scope,
            VendorPayableRecord(
                id = invoice.id.value,
                vendorId = invoice.partyId.value,
                documentNumber = invoice.invoiceNumber,
                status = invoice.status.name,
                totalAmount = posting.totals.grandTotal.amount,
                paidAmount = invoice.paidAmount.amount,
                currency = posting.totals.grandTotal.currency.value,
                date = invoice.date.toString(),
                dueDate = invoice.dueDate?.toString() ?: "",
                createdAt = now.toString(),
            ),
        )
        return PurchasePostResult.Posted(invoice.invoiceNumber)
    }

    suspend fun postDebitNote(
        scope: OperationalScope,
        debitNoteId: String,
        documentNumber: String,
        partyId: PartyId,
        lines: List<DebitNoteLine>,
        accounts: DebitNotePostingAccounts,
        date: LocalDate,
        accountsViewModel: AccountsViewModel,
        inventoryViewModel: InventoryViewModel,
    ): PurchasePostResult {
        val posting = PurchaseDebitNotePosting.debitNoteToPosting(
            debitNoteId, documentNumber, partyId, lines, accounts,
        )
        val now = Instant.now()
        val result = accountsViewModel.postDocument(
            scope = scope,
            posting = posting,
            date = date,
            now = now,
            deviceId = DeviceId(scope.deviceId),
        )
        if (result is AccountsViewModel.DocumentPostResult.Rejected) {
            return PurchasePostResult.Rejected(result.violations.map { it.name })
        }
        val alreadyMoved = inventoryViewModel.loadMovements(scope)
            .any { it.referenceId == debitNoteId }
        if (!alreadyMoved) {
            posting.stockMovements.forEach { movement ->
                inventoryViewModel.saveMovement(
                    scope,
                    StockMovementRecord(
                        id = java.util.UUID.randomUUID().toString(),
                        itemId = movement.itemId.value,
                        warehouseId = movement.warehouseId?.value,
                        batchId = movement.batchId,
                        type = MovementType.PURCHASE,
                        quantity = movement.quantity.amount.negate(),
                        rate = movement.rate?.amount,
                        referenceType = "DEBIT_NOTE",
                        referenceId = debitNoteId,
                        date = date.toString(),
                        reason = "DEBIT_NOTE $documentNumber",
                        createdAt = now.toString(),
                    ),
                )
            }
        }
        return PurchasePostResult.Posted(documentNumber)
    }
}
