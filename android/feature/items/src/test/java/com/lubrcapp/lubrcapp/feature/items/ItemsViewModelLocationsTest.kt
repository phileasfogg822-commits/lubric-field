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

/** Phase 16.2: branches and warehouses land on their own tables through their codecs. */
class ItemsViewModelLocationsTest {

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

    @Test fun branchCrudUsesBranchesTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val branch = Branch(id = "br-1", name = "Main Shop")
        vm.saveBranch(scope, branch)
        assertEquals("branches", fake.savedTable)
        assertEquals(listOf(branch), vm.loadBranches(scope))
        assertEquals(listOf(branch), vm.observeBranches(scope).first())

        vm.deleteBranch(scope, "br-1")
        assertEquals("branches", fake.deletedTable)
        assertEquals(emptyList<Branch>(), vm.loadBranches(scope))
    }

    @Test fun warehouseCrudUsesWarehousesTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val warehouse = Warehouse(id = "w-1", name = "Godown", branchId = "br-1")
        vm.saveWarehouse(scope, warehouse)
        assertEquals("warehouses", fake.savedTable)
        assertEquals(listOf(warehouse), vm.loadWarehouses(scope))
        assertEquals(listOf(warehouse), vm.observeWarehouses(scope).first())

        vm.deleteWarehouse(scope, "w-1")
        assertEquals("warehouses", fake.deletedTable)
        assertEquals(emptyList<Warehouse>(), vm.loadWarehouses(scope))
    }

    @Test fun legacyLocationPayloadsDecodeThroughViewModel() = runBlocking {
        val fake = FakeRepository()
        fake.tables["branches"] = mutableListOf(OperationalRecord("br-7", """{"uuid":"u1","name":"Legacy Branch"}"""))
        fake.tables["warehouses"] = mutableListOf(OperationalRecord("w-7", """{"name":"Legacy Warehouse"}"""))
        val vm = ItemsViewModel(fake)
        assertEquals("Legacy Branch", vm.loadBranches(scope).single().name)
        assertEquals("Legacy Warehouse", vm.loadWarehouses(scope).single().name)
    }
}
