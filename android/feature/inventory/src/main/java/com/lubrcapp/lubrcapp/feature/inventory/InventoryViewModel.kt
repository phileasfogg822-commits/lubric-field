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
            when (StockLedger.checkNegative(policy, balance, quantity)) {
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
}
