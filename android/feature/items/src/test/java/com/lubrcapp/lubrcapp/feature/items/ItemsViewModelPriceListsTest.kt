package com.lubrcapp.lubrcapp.feature.items

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.3: price lists and entries land on their own tables through their codecs. */
class ItemsViewModelPriceListsTest {

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

    @Test fun priceListCrudUsesPriceListsTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val list = PriceList(id = "pl-1", name = "Wholesale")
        vm.savePriceList(scope, list)
        assertEquals("price_lists", fake.savedTable)
        assertEquals(listOf(list), vm.loadPriceLists(scope))
        assertEquals(listOf(list), vm.observePriceLists(scope).first())

        vm.deletePriceList(scope, "pl-1")
        assertEquals("price_lists", fake.deletedTable)
        assertEquals(emptyList<PriceList>(), vm.loadPriceLists(scope))
    }

    @Test fun entryCrudUsesPriceListItemsTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val entry = PriceListItem(id = "e-1", priceListId = "pl-1", itemId = "i-1", rate = BigDecimal("950"))
        vm.savePriceListItem(scope, entry)
        assertEquals("price_list_items", fake.savedTable)
        assertEquals(listOf(entry), vm.loadPriceListItems(scope))
        assertEquals(listOf(entry), vm.observePriceListItems(scope).first())

        vm.deletePriceListItem(scope, "e-1")
        assertEquals("price_list_items", fake.deletedTable)
        assertEquals(emptyList<PriceListItem>(), vm.loadPriceListItems(scope))
    }
}
