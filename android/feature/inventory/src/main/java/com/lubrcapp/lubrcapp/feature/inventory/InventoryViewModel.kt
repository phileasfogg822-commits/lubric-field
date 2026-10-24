package com.lubrcapp.lubrcapp.feature.inventory

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.inventory.LedgerMovement
import com.lubrcapp.lubrcapp.domain.inventory.NegativeStockDecision
import com.lubrcapp.lubrcapp.domain.inventory.NegativeStockPolicy
import com.lubrcapp.lubrcapp.domain.inventory.StockLedger
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first inventory feature: observes Room and queues writes without network access. */
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "stock_movements")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "stock_movements")

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "stock_movements", id, payloadJson, revision)

    // Phase 19.1 typed movement surface. The raw record methods
    // above stay as the low-level passthrough (pinned by
    // InventoryViewModelTest); everything ledger-shaped goes
    // through these. The ledger is append-only (§181): there is
    // deliberately NO movement update or delete here — a
    // correction is a new reversing movement via saveMovement.

    fun observeMovements(scope: OperationalScope): Flow<List<StockMovementRecord>> =
        repository.observe(scope, "stock_movements").map { records ->
            records.map { StockMovementJson.decode(it.id, it.json) }.sortedForLedger()
        }

    suspend fun loadMovements(scope: OperationalScope): List<StockMovementRecord> =
        repository.list(scope, "stock_movements")
            .map { StockMovementJson.decode(it.id, it.json) }
            .sortedForLedger()

    suspend fun loadMovementsForItem(scope: OperationalScope, itemId: String): List<StockMovementRecord> =
        loadMovements(scope).filter { it.itemId == itemId }

    suspend fun saveMovement(scope: OperationalScope, movement: StockMovementRecord) =
        repository.save(scope, "stock_movements", movement.id, StockMovementJson.encode(movement), 0)

    /**
     * The movement-writing surface the rest of the app posts
     * through (19.4 adjustments, 19.5 transfers, Phase 21 document
     * posting): builds a movement from an unsigned [quantity] —
     * the sign comes from [type]'s direction — stamps it with a
     * fresh id and the current instant, and appends it.
     */
    suspend fun postMovement(
        scope: OperationalScope,
        itemId: String,
        type: MovementType,
        quantity: BigDecimal,
        warehouseId: String? = null,
        batchId: String? = null,
        rate: BigDecimal? = null,
        referenceType: String? = null,
        referenceId: String? = null,
        date: String = "",
        reason: String = "",
        attachmentUri: String? = null,
    ): StockMovementRecord {
        val movement = StockMovementRecord.of(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            type = type,
            quantity = quantity,
            warehouseId = warehouseId,
            batchId = batchId,
            rate = rate,
            referenceType = referenceType,
            referenceId = referenceId,
            date = date,
            reason = reason,
            attachmentUri = attachmentUri,
            createdAt = Instant.now().toString(),
        )
        saveMovement(scope, movement)
        return movement
    }

    private fun List<StockMovementRecord>.sortedForLedger(): List<StockMovementRecord> =
        sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))

    // Phase 19.3 screen surface: reference data for the §31
    // summary and the §33 levels records ("stock_levels",
    // local-only — see StockLevelsRecord). Levels are keyed by
    // item id, so saving is a plain upsert of that item's record.

    fun observeItems(scope: OperationalScope): Flow<List<ItemStockOption>> =
        repository.observe(scope, "products").map { InventoryReferenceData.itemOptions(it) }

    suspend fun loadItems(scope: OperationalScope): List<ItemStockOption> =
        InventoryReferenceData.itemOptions(repository.list(scope, "products"))

    fun observeWarehouses(scope: OperationalScope): Flow<List<NamedOption>> =
        repository.observe(scope, "warehouses").map { InventoryReferenceData.namedOptions(it) }

    fun observeCategories(scope: OperationalScope): Flow<List<NamedOption>> =
        repository.observe(scope, "categories").map { InventoryReferenceData.namedOptions(it) }

    fun observeBrands(scope: OperationalScope): Flow<List<NamedOption>> =
        repository.observe(scope, "brands").map { InventoryReferenceData.namedOptions(it) }

    fun observeLevels(scope: OperationalScope): Flow<Map<String, StockLevelsRecord>> =
        repository.observe(scope, "stock_levels").map { records ->
            records.associate { it.id to StockLevelsJson.decode(it.id, it.json) }
        }

    suspend fun loadLevels(scope: OperationalScope): Map<String, StockLevelsRecord> =
        repository.list(scope, "stock_levels")
            .associate { it.id to StockLevelsJson.decode(it.id, it.json) }

    suspend fun saveLevels(scope: OperationalScope, levels: StockLevelsRecord) =
        repository.save(scope, "stock_levels", levels.itemId, StockLevelsJson.encode(levels), 0)

    // Phase 19.4 adjustment + count posting. Both go through the
    // §175 negative-stock check for outbound movements: the
    // balance is the ledger balance of the exact dimension the
    // movement leaves from (the item total when no warehouse is
    // named; the opening constant counts only in that total).
    // The policy is a parameter — WARN until a settings home
    // exists, never silently NEVER_ALLOW (phase prep decision).

    suspend fun postAdjustment(
        scope: OperationalScope,
        form: StockAdjustmentFormState,
        policy: NegativeStockPolicy = NegativeStockPolicy.WARN,
    ): AdjustmentResult {
        val itemId = form.itemId ?: return AdjustmentResult.BlockedByPolicy
        val quantity = form.quantity ?: return AdjustmentResult.BlockedByPolicy
        if (!form.isValid) return AdjustmentResult.BlockedByPolicy
        return postChecked(
            scope = scope,
            itemId = itemId,
            type = form.movementType,
            quantity = quantity,
            warehouseId = form.warehouseId,
            batchId = form.batchId,
            date = form.date,
            reason = form.reason,
            attachmentUri = form.attachmentUri,
            policy = policy,
        )
    }

    /** §37: post one count variance ("Adjust Stock"). Zero variance posts nothing and returns null. */
    suspend fun postCountVariance(
        scope: OperationalScope,
        itemId: String,
        variance: BigDecimal,
        warehouseId: String? = null,
        date: String = "",
        reason: String,
        policy: NegativeStockPolicy = NegativeStockPolicy.WARN,
    ): AdjustmentResult? {
        if (variance.signum() == 0) return null
        val type = if (variance.signum() > 0) MovementType.ADJUSTMENT_IN else MovementType.ADJUSTMENT_OUT
        return postChecked(
            scope = scope,
            itemId = itemId,
            type = type,
            quantity = variance.abs(),
            warehouseId = warehouseId,
            batchId = null,
            date = date,
            reason = reason,
            attachmentUri = null,
            policy = policy,
        )
    }

    /**
     * The §175 decision for one outbound movement of [quantity]
     * from the exact dimension it leaves: the item total (with
     * the opening constant) when no warehouse is named, else
     * that warehouse/batch dimension's ledger balance.
     */
    private suspend fun outboundDecision(
        scope: OperationalScope,
        itemId: String,
        warehouseId: String?,
        batchId: String?,
        quantity: BigDecimal,
        policy: NegativeStockPolicy,
    ): NegativeStockDecision {
        val movements = loadMovements(scope)
        val ledgerMovements = movements.map {
            LedgerMovement(itemId = it.itemId, warehouseId = it.warehouseId, batchId = it.batchId, quantity = it.quantity)
        }
        val opening = loadItems(scope).firstOrNull { it.id == itemId }?.openingStock ?: BigDecimal.ZERO
        val balance = if (warehouseId == null) {
            StockLedger.totalFor(ledgerMovements, itemId, opening)
        } else {
            StockLedger.balanceFor(ledgerMovements, itemId, warehouseId = warehouseId, batchId = batchId)
        }
        return StockLedger.checkNegative(policy, balance, quantity)
    }

    private suspend fun postChecked(
        scope: OperationalScope,
        itemId: String,
        type: MovementType,
        quantity: BigDecimal,
        warehouseId: String?,
        batchId: String?,
        date: String,
        reason: String,
        attachmentUri: String?,
        policy: NegativeStockPolicy,
    ): AdjustmentResult {
        var warning = false
        if (!type.isInbound) {
            when (outboundDecision(scope, itemId, warehouseId, batchId, quantity, policy)) {
                NegativeStockDecision.BLOCKED -> return AdjustmentResult.BlockedByPolicy
                NegativeStockDecision.ALLOWED_WITH_WARNING -> warning = true
                NegativeStockDecision.ALLOWED -> Unit
            }
        }
        val movement = postMovement(
            scope = scope,
            itemId = itemId,
            type = type,
            quantity = quantity,
            warehouseId = warehouseId,
            batchId = batchId,
            referenceType = "STOCK_ADJUSTMENT",
            date = date,
            reason = reason,
            attachmentUri = attachmentUri,
        )
        return AdjustmentResult.Posted(movement, warning)
    }

    // Phase 19.5 transfers (§35) over "stock_transfers"
    // (local-only header with embedded lines — see
    // StockTransferRecord). Status actions post the paired
    // movements through postMovement with referenceType
    // "STOCK_TRANSFER" and the transfer id as referenceId, so the
    // ledger alone explains every unit that moved.

    fun observeTransfers(scope: OperationalScope): Flow<List<StockTransferRecord>> =
        repository.observe(scope, "stock_transfers").map { records ->
            records.map { StockTransferJson.decode(it.id, it.json) }.sortedForDisplay()
        }

    suspend fun loadTransfers(scope: OperationalScope): List<StockTransferRecord> =
        repository.list(scope, "stock_transfers")
            .map { StockTransferJson.decode(it.id, it.json) }
            .sortedForDisplay()

    suspend fun saveTransfer(scope: OperationalScope, transfer: StockTransferRecord) =
        repository.save(scope, "stock_transfers", transfer.id, StockTransferJson.encode(transfer), 0)

    private fun List<StockTransferRecord>.sortedForDisplay(): List<StockTransferRecord> =
        sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))

    /** Save a new transfer as a DRAFT; null when the form is not postable. */
    suspend fun createTransfer(
        scope: OperationalScope,
        form: StockTransferFormState,
    ): StockTransferRecord? {
        if (!form.isValid) return null
        val transfer = StockTransferRecord(
            id = UUID.randomUUID().toString(),
            fromWarehouseId = form.fromWarehouseId!!,
            toWarehouseId = form.toWarehouseId!!,
            status = TransferStatus.DRAFT,
            date = form.date,
            note = form.note,
            lines = form.validLines,
            createdAt = Instant.now().toString(),
        )
        saveTransfer(scope, transfer)
        return transfer
    }

    /**
     * DRAFT → DISPATCHED: posts TRANSFER_OUT at the source for
     * every line. All lines are §175-checked BEFORE any posting —
     * a blocked line blocks the whole dispatch, so a transfer is
     * never half-dispatched. Lines are grouped per item first:
     * the check must see the total leaving, not line-by-line
     * slices of it.
     */
    suspend fun dispatchTransfer(
        scope: OperationalScope,
        transferId: String,
        policy: NegativeStockPolicy = NegativeStockPolicy.WARN,
    ): TransferResult {
        val transfer = loadTransfers(scope).firstOrNull { it.id == transferId }
            ?: return TransferResult.InvalidState
        if (transfer.status != TransferStatus.DRAFT) return TransferResult.InvalidState
        val totalsByItem = transfer.lines
            .filter { it.quantity.signum() > 0 }
            .groupBy { it.itemId }
            .mapValues { (_, lines) -> lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.quantity) } }
        var warning = false
        for ((itemId, total) in totalsByItem) {
            when (outboundDecision(scope, itemId, transfer.fromWarehouseId, null, total, policy)) {
                NegativeStockDecision.BLOCKED -> return TransferResult.BlockedByPolicy
                NegativeStockDecision.ALLOWED_WITH_WARNING -> warning = true
                NegativeStockDecision.ALLOWED -> Unit
            }
        }
        for (line in transfer.lines) {
            if (line.quantity.signum() <= 0) continue
            postMovement(
                scope = scope,
                itemId = line.itemId,
                type = MovementType.TRANSFER_OUT,
                quantity = line.quantity,
                warehouseId = transfer.fromWarehouseId,
                referenceType = "STOCK_TRANSFER",
                referenceId = transfer.id,
                date = transfer.date,
                reason = transfer.note,
            )
        }
        val updated = transfer.copy(status = TransferStatus.DISPATCHED)
        saveTransfer(scope, updated)
        return TransferResult.Done(updated, warning)
    }

    /** DISPATCHED → RECEIVED: posts TRANSFER_IN at the destination for every line. */
    suspend fun receiveTransfer(
        scope: OperationalScope,
        transferId: String,
    ): TransferResult {
        val transfer = loadTransfers(scope).firstOrNull { it.id == transferId }
            ?: return TransferResult.InvalidState
        if (transfer.status != TransferStatus.DISPATCHED) return TransferResult.InvalidState
        for (line in transfer.lines) {
            if (line.quantity.signum() <= 0) continue
            postMovement(
                scope = scope,
                itemId = line.itemId,
                type = MovementType.TRANSFER_IN,
                quantity = line.quantity,
                warehouseId = transfer.toWarehouseId,
                referenceType = "STOCK_TRANSFER",
                referenceId = transfer.id,
                date = transfer.date,
                reason = transfer.note,
            )
        }
        val updated = transfer.copy(status = TransferStatus.RECEIVED)
        saveTransfer(scope, updated)
        return TransferResult.Done(updated)
    }

    /** DRAFT → CANCELLED only: a dispatched transfer must be received (see StockTransferRecord). */
    suspend fun cancelTransfer(
        scope: OperationalScope,
        transferId: String,
    ): TransferResult {
        val transfer = loadTransfers(scope).firstOrNull { it.id == transferId }
            ?: return TransferResult.InvalidState
        if (transfer.status != TransferStatus.DRAFT) return TransferResult.InvalidState
        val updated = transfer.copy(status = TransferStatus.CANCELLED)
        saveTransfer(scope, updated)
        return TransferResult.Done(updated)
    }

    // Phase 19.6 serials (§30) over "serial_numbers"
    // (local-only registry — see StockSerialRecord). Serials are
    // never deleted: they leave by status, and the IN_STOCK set
    // for an item is what its detail screen lists.

    fun observeSerials(scope: OperationalScope): Flow<List<StockSerialRecord>> =
        repository.observe(scope, "serial_numbers").map { records ->
            records.map { StockSerialJson.decode(it.id, it.json) }.sortedSerialsForDisplay()
        }

    suspend fun loadSerials(scope: OperationalScope): List<StockSerialRecord> =
        repository.list(scope, "serial_numbers")
            .map { StockSerialJson.decode(it.id, it.json) }
            .sortedSerialsForDisplay()

    suspend fun loadSerialsForItem(scope: OperationalScope, itemId: String): List<StockSerialRecord> =
        loadSerials(scope).filter { it.itemId == itemId }

    suspend fun saveSerial(scope: OperationalScope, serial: StockSerialRecord) =
        repository.save(scope, "serial_numbers", serial.id, StockSerialJson.encode(serial), 0)

    private fun List<StockSerialRecord>.sortedSerialsForDisplay(): List<StockSerialRecord> =
        sortedWith(compareBy({ it.itemId }, { it.serialNumber }, { it.id }))

    /**
     * The receive flow: register one serial as IN_STOCK. The
     * same serial may exist once per item on the shelf — a
     * second IN_STOCK registration for the same item is a
     * Duplicate; re-registering after it left (SOLD / REMOVED)
     * is a new record, preserving the audit trail.
     */
    suspend fun addSerial(
        scope: OperationalScope,
        itemId: String,
        serialNumber: String,
        warehouseId: String? = null,
        date: String = "",
    ): SerialAddResult {
        val trimmed = serialNumber.trim()
        if (trimmed.isEmpty()) return SerialAddResult.Invalid
        val duplicate = loadSerials(scope).any {
            it.itemId == itemId && it.serialNumber == trimmed && it.status == SerialStatus.IN_STOCK
        }
        if (duplicate) return SerialAddResult.Duplicate
        val record = StockSerialRecord(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            serialNumber = trimmed,
            status = SerialStatus.IN_STOCK,
            warehouseId = warehouseId,
            date = date,
            createdAt = Instant.now().toString(),
        )
        saveSerial(scope, record)
        return SerialAddResult.Added(record)
    }

    /**
     * The adjust flow's write-off: IN_STOCK → REMOVED with the
     * reason kept on the record. Returns the updated record, or
     * null when the serial is unknown or no longer in stock.
     * Phase 21's sale posting will flip IN_STOCK → SOLD through
     * saveSerial the same way.
     */
    suspend fun removeSerial(
        scope: OperationalScope,
        serialId: String,
        reason: String,
    ): StockSerialRecord? {
        val serial = loadSerials(scope).firstOrNull { it.id == serialId } ?: return null
        if (serial.status != SerialStatus.IN_STOCK) return null
        val updated = serial.copy(status = SerialStatus.REMOVED, note = reason)
        saveSerial(scope, updated)
        return updated
    }
}
