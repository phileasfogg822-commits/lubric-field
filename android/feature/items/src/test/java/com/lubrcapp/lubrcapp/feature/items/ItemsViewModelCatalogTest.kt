package com.lubrcapp.lubrcapp.feature.items

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 15.3: categories and brands land on their own tables through their codecs. */
class ItemsViewModelCatalogTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        var savedTable: String? = null
        var deletedTable: String? = null

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(tables[table].orEmpty())

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            tables[table].orEmpty()

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            savedTable = table
            val list = tables.getOrPut(table) { mutableListOf() }
            list.removeAll { it.id == id }
            list.add(OperationalRecord(id, json))
        }

        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            deletedTable = table
            tables[table]?.removeAll { it.id == id }
        }
    }

    @Test fun categoryCrudUsesCategoriesTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val category = ItemCategory(id = "c-1", name = "Engine Oils", parentId = "c-0")
        vm.saveCategory(scope, category)
        assertEquals("categories", fake.savedTable)
        assertEquals(listOf(category), vm.loadCategories(scope))
        assertEquals(listOf(category), vm.observeCategories(scope).first())

        vm.deleteCategory(scope, "c-1")
        assertEquals("categories", fake.deletedTable)
        assertEquals(emptyList<ItemCategory>(), vm.loadCategories(scope))
    }

    @Test fun brandCrudUsesBrandsTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val brand = Brand(id = "b-1", name = "Shell")
        vm.saveBrand(scope, brand)
        assertEquals("brands", fake.savedTable)
        assertEquals(listOf(brand), vm.loadBrands(scope))
        assertEquals(listOf(brand), vm.observeBrands(scope).first())

        vm.deleteBrand(scope, "b-1")
        assertEquals("brands", fake.deletedTable)
        assertEquals(emptyList<Brand>(), vm.loadBrands(scope))
    }

    @Test fun legacyCatalogPayloadsDecodeThroughViewModel() = runBlocking {
        val fake = FakeRepository()
        fake.tables["categories"] = mutableListOf(OperationalRecord("c-7", """{"uuid":"u1","name":"Legacy Cat"}"""))
        fake.tables["brands"] = mutableListOf(OperationalRecord("b-7", """{"name":"Legacy Brand"}"""))
        val vm = ItemsViewModel(fake)
        assertEquals("Legacy Cat", vm.loadCategories(scope).single().name)
        assertEquals("Legacy Brand", vm.loadBrands(scope).single().name)
    }
}
