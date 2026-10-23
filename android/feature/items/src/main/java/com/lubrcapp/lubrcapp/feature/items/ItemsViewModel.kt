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
}
