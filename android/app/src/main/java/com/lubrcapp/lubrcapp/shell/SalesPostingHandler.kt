package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.inventory.MovementType
import com.lubrcapp.lubrcapp.feature.inventory.StockMovementRecord
import com.lubrcapp.lubrcapp.feature.sales.InvoicePostingAccounts
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesInvoicePosting
import com.lubrcapp.lubrcapp.feature.sales.SalesPostResult
import com.lubrcapp.lubrcapp.feature.sales.SalesReturnPosting
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 21.5 — the app-layer posting join. This is the one
 * place that may see all three ViewModels at once: feature
 * modules do not depend on each other, so the join cannot
 * live in feature/sales — it lives here, in the shell, which
 * already depends on every feature. The sales screen owns
 * the buttons and the account picker; this handler owns the
 * sequence, and the sequence order is the whole point:
 *
 * 1. Load the document's lines through the sales ViewModel.
 * 2. Build the posting with the 21.3/21.4 builders.
 * 3. Post through AccountsViewModel.postDocument (20.2).
 * 4. ONLY on Posted: persist the posting's stock movements
 *    through the inventory ViewModel (the 19.1 ledger
 *    convention: SALE outbound negative, RETURN inbound
 *    positive, keyed by this document as referenceId), then
 *    flip the lines' serials (SOLD for invoices, back to
 *    stock for returns). A rejected posting never moves
 *    stock or serials.
 * 5. Retry safety: the journal overwrites by derived id
 *    (20.2), serial flips are no-ops once flipped, and
 *    movements are skipped when this document already has
 *    rows — so re-posting after a mid-sequence failure
 *    cannot double-count stock.
 *
 * Posting does not change the document's status: status
 * moves through posting-adjacent flows (payments land in
 * Phase 22), not through the journal. The document number
 * in the result is what the screen's notice shows; an
 * unnumbered document posts under its blank number, the
 * same way the engine names the entry.
 */
object SalesPostingHandler {

    suspend fun postDocument(
        scope: OperationalScope,
        document: SalesDocument,
        accounts: InvoicePostingAccounts,
        currency: CurrencyCode,
        salesViewModel: SalesViewModel,
        accountsViewModel: AccountsViewModel,
        inventoryViewModel: InventoryViewModel,
    ): SalesPostResult {
        val lines = salesViewModel.loadLines(scope, document.id)
        val posting = when (document.type) {
            SalesDocumentType.INVOICE ->
                SalesInvoicePosting.invoiceToPosting(document, lines, currency, accounts)
            SalesDocumentType.SALES_RETURN ->
                SalesReturnPosting.returnToPosting(document, lines, currency, accounts)
            SalesDocumentType.CREDIT_NOTE ->
                SalesReturnPosting.creditNoteToPosting(document, lines, currency, accounts)
            else -> return SalesPostResult.Rejected(listOf("Cannot post ${document.type}"))
        }
        val date = runCatching { LocalDate.parse(document.date) }.getOrNull()
            ?: return SalesPostResult.Rejected(listOf("Invalid date"))
        val now = Instant.now()
        val result = accountsViewModel.postDocument(
            scope = scope,
            posting = posting,
            date = date,
            now = now,
            deviceId = DeviceId(scope.deviceId),
        )
        if (result is AccountsViewModel.DocumentPostResult.Rejected) {
            return SalesPostResult.Rejected(result.violations.map { it.name })
        }
        val alreadyMoved = inventoryViewModel.loadMovements(scope)
            .any { it.referenceId == document.id }
        if (!alreadyMoved) {
            posting.stockMovements.forEach { movement ->
                inventoryViewModel.saveMovement(
                    scope,
                    StockMovementRecord(
                        id = java.util.UUID.randomUUID().toString(),
                        itemId = movement.itemId.value,
                        warehouseId = movement.warehouseId?.value,
                        batchId = movement.batchId,
                        type = if (movement.kind == StockMovementKind.OUT) {
                            MovementType.SALE
                        } else {
                            MovementType.RETURN
                        },
                        quantity = if (movement.kind == StockMovementKind.OUT) {
                            movement.quantity.amount.negate()
                        } else {
                            movement.quantity.amount
                        },
                        rate = movement.rate?.amount,
                        referenceType = document.type.name,
                        referenceId = document.id,
                        date = document.date,
                        reason = "${document.type.name} ${document.number}",
                        createdAt = now.toString(),
                    ),
                )
            }
        }
        val flipped = mutableListOf<String>()
        if (document.type == SalesDocumentType.INVOICE ||
            document.type == SalesDocumentType.SALES_RETURN
        ) {
            val byItem = lines
                .filter { it.itemId != null && it.serialNumbers.isNotEmpty() }
                .groupBy { it.itemId!! }
            for ((itemId, itemLines) in byItem) {
                val numbers = itemLines.flatMap { it.serialNumbers }.distinct()
                // The two flip results are unrelated types that
                // happen to share a `flipped` shape — an if/else
                // over them resolves to Any and `.flipped` does
                // not compile. Branch the call, not the value.
                if (document.type == SalesDocumentType.INVOICE) {
                    val flip = inventoryViewModel.markSerialsSold(scope, itemId, numbers)
                    flipped += flip.flipped.map { it.serialNumber }
                } else {
                    val flip = inventoryViewModel.markSerialsReturned(scope, itemId, numbers)
                    flipped += flip.flipped.map { it.serialNumber }
                }
            }
        }
        return SalesPostResult.Posted(document.number, flipped)
    }
}
