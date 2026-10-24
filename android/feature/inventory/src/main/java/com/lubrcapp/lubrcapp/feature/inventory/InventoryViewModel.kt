package com.lubrcapp.lubrcapp.feature.inventory

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
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
}
