package com.lubrcapp.lubrcapp.feature.items

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first items feature: observes Room and queues writes without network access. */
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "products")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "products")

    suspend fun delete(scope: OperationalScope, id: String) = repository.delete(scope, "products", id)

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "products", id, payloadJson, revision)

    // Phase 15.1 — typed access over the same store. The codec tolerates
    // legacy free-form payloads, so typed reads are safe on old records.
    // The raw methods above remain as the low-level store passthrough,
    // pinned by ItemsViewModelTest; the screen moves to this typed
    // surface in Package 15.2.

    /** Typed items: every record decoded through [ItemJson]. */
    fun observeItems(scope: OperationalScope): Flow<List<Item>> =
        observe(scope).map { records -> records.map { ItemJson.decode(it.id, it.json) } }

    suspend fun loadItems(scope: OperationalScope): List<Item> =
        load(scope).map { ItemJson.decode(it.id, it.json) }

    /** Saves a typed item through the codec (revision passes through to the store). */
    suspend fun saveItem(scope: OperationalScope, item: Item, revision: Long = 0) =
        save(scope, item.id, ItemJson.encode(item), revision)

    // Phase 15.3 — categories (§23) and brands (§24) as their own
    // record types in the same store. Deleting one uses the same
    // tombstone path as items and never touches the items or
    // subcategories that link to it: their ids stay and render
    // unresolved until the record exists again.

    /** Typed categories: every record decoded through [ItemCategoryJson]. */
    fun observeCategories(scope: OperationalScope): Flow<List<ItemCategory>> =
        repository.observe(scope, "categories")
            .map { records -> records.map { ItemCategoryJson.decode(it.id, it.json) } }

    suspend fun loadCategories(scope: OperationalScope): List<ItemCategory> =
        repository.list(scope, "categories").map { ItemCategoryJson.decode(it.id, it.json) }

    suspend fun saveCategory(scope: OperationalScope, category: ItemCategory, revision: Long = 0) =
        repository.save(scope, "categories", category.id, ItemCategoryJson.encode(category), revision)

    /** Categories are nonfinancial master data: soft-delete by tombstone, like items. */
    suspend fun deleteCategory(scope: OperationalScope, id: String) =
        repository.delete(scope, "categories", id)

    /** Typed brands: every record decoded through [BrandJson]. */
    fun observeBrands(scope: OperationalScope): Flow<List<Brand>> =
        repository.observe(scope, "brands")
            .map { records -> records.map { BrandJson.decode(it.id, it.json) } }

    suspend fun loadBrands(scope: OperationalScope): List<Brand> =
        repository.list(scope, "brands").map { BrandJson.decode(it.id, it.json) }

    suspend fun saveBrand(scope: OperationalScope, brand: Brand, revision: Long = 0) =
        repository.save(scope, "brands", brand.id, BrandJson.encode(brand), revision)

    /** Brands are nonfinancial master data: soft-delete by tombstone, like items. */
    suspend fun deleteBrand(scope: OperationalScope, id: String) =
        repository.delete(scope, "brands", id)

    // Phase 16.1 — units (§25) and conversions as their own record
    // types in the same store. Deleting a unit uses the same
    // tombstone path and never touches the items or conversions
    // that link to it: their ids stay and render unresolved.

    /** Typed units: every record decoded through [ItemUnitJson]. */
    fun observeUnits(scope: OperationalScope): Flow<List<ItemUnit>> =
        repository.observe(scope, "units")
            .map { records -> records.map { ItemUnitJson.decode(it.id, it.json) } }

    suspend fun loadUnits(scope: OperationalScope): List<ItemUnit> =
        repository.list(scope, "units").map { ItemUnitJson.decode(it.id, it.json) }

    suspend fun saveUnit(scope: OperationalScope, unit: ItemUnit, revision: Long = 0) =
        repository.save(scope, "units", unit.id, ItemUnitJson.encode(unit), revision)

    /** Units are nonfinancial master data: soft-delete by tombstone, like items. */
    suspend fun deleteUnit(scope: OperationalScope, id: String) =
        repository.delete(scope, "units", id)

    /** Typed conversions: every record decoded through [UnitConversionJson]. */
    fun observeConversions(scope: OperationalScope): Flow<List<UnitConversion>> =
        repository.observe(scope, "unit_conversions")
            .map { records -> records.map { UnitConversionJson.decode(it.id, it.json) } }

    suspend fun loadConversions(scope: OperationalScope): List<UnitConversion> =
        repository.list(scope, "unit_conversions").map { UnitConversionJson.decode(it.id, it.json) }

    suspend fun saveConversion(scope: OperationalScope, conversion: UnitConversion, revision: Long = 0) =
        repository.save(scope, "unit_conversions", conversion.id, UnitConversionJson.encode(conversion), revision)

    /** Conversions are nonfinancial master data: soft-delete by tombstone, like items. */
    suspend fun deleteConversion(scope: OperationalScope, id: String) =
        repository.delete(scope, "unit_conversions", id)

    // Phase 16.2 — branches and warehouses (§34) as their own
    // record types in the same store. Master data only: no stock
    // and no transactions live here (Phases 18–21 own those, the
    // §35 transfer workflow is Phase 19). Deleting a branch uses
    // the same tombstone path and never touches its warehouses:
    // their branch ids stay and render unresolved.

    /** Typed branches: every record decoded through [BranchJson]. */
    fun observeBranches(scope: OperationalScope): Flow<List<Branch>> =
        repository.observe(scope, "branches")
            .map { records -> records.map { BranchJson.decode(it.id, it.json) } }

    suspend fun loadBranches(scope: OperationalScope): List<Branch> =
        repository.list(scope, "branches").map { BranchJson.decode(it.id, it.json) }

    suspend fun saveBranch(scope: OperationalScope, branch: Branch, revision: Long = 0) =
        repository.save(scope, "branches", branch.id, BranchJson.encode(branch), revision)

    /** Branches are nonfinancial master data: soft-delete by tombstone, like items. */
    suspend fun deleteBranch(scope: OperationalScope, id: String) =
        repository.delete(scope, "branches", id)

    /** Typed warehouses: every record decoded through [WarehouseJson]. */
    fun observeWarehouses(scope: OperationalScope): Flow<List<Warehouse>> =
        repository.observe(scope, "warehouses")
            .map { records -> records.map { WarehouseJson.decode(it.id, it.json) } }

    suspend fun loadWarehouses(scope: OperationalScope): List<Warehouse> =
        repository.list(scope, "warehouses").map { WarehouseJson.decode(it.id, it.json) }

    suspend fun saveWarehouse(scope: OperationalScope, warehouse: Warehouse, revision: Long = 0) =
        repository.save(scope, "warehouses", warehouse.id, WarehouseJson.encode(warehouse), revision)

    /** Warehouses are nonfinancial master data: soft-delete by tombstone, like items. */
    suspend fun deleteWarehouse(scope: OperationalScope, id: String) =
        repository.delete(scope, "warehouses", id)
}
