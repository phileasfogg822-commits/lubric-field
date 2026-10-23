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

/** Phase 15.1: the ViewModel's typed surface decodes/encodes through ItemJson. */
class ItemsViewModelTypedTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository(
        private val records: List<OperationalRecord>,
    ) : OperationalBusinessRepository {
        var savedId: String? = null
        var savedJson: String? = null
        var savedTable: String? = null

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(records)

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            records

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            savedTable = table
            savedId = id
            savedJson = json
        }

        override suspend fun delete(scope: OperationalScope, table: String, id: String) = Unit
    }

    @Test fun loadItemsDecodesLegacyAndTypedRecords() = runBlocking {
        val legacy = OperationalRecord("i-1", """{"uuid":"u1","name":"Legacy Item"}""")
        val typedItem = Item(
            id = "i-2",
            name = "Typed Item",
            type = ItemType.SERVICE,
            saleRate = BigDecimal("1500"),
        )
        val typed = OperationalRecord("i-2", ItemJson.encode(typedItem))
        val vm = ItemsViewModel(FakeRepository(listOf(legacy, typed)))

        val items = vm.loadItems(scope)
        assertEquals(2, items.size)
        assertEquals("Legacy Item", items[0].name)
        assertEquals(ItemType.PRODUCT, items[0].type)
        assertEquals(typedItem, items[1])
    }

    @Test fun observeItemsMapsThroughCodec() = runBlocking {
        val record = OperationalRecord("i-1", """{"name":"Observed","description":"a note"}""")
        val vm = ItemsViewModel(FakeRepository(listOf(record)))
        val items = vm.observeItems(scope).first()
        assertEquals(1, items.size)
        assertEquals("Observed", items.single().name)
        assertEquals("a note", items.single().description)
    }

    @Test fun saveItemEncodesThroughCodecToProductsTable() = runBlocking {
        val fake = FakeRepository(emptyList())
        val vm = ItemsViewModel(fake)
        val item = Item(
            id = "i-7",
            name = "Saved Item",
            type = ItemType.BUNDLE,
            purchaseRate = BigDecimal("2000"),
        )
        vm.saveItem(scope, item)
        assertEquals("products", fake.savedTable)
        assertEquals("i-7", fake.savedId)
        assertEquals(item, ItemJson.decode("i-7", fake.savedJson!!))
    }
}
