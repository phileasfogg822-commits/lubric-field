package com.lubrcapp.lubrcapp.feature.items

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemsViewModelTest {
    @Test fun saveLoadAndObserveUseScopedProductsRepository() = runBlocking {
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val calls = mutableListOf<String>()
        val fake = object : OperationalBusinessRepository {
            override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> {
                calls.add("observe:" + table)
                return flowOf(listOf(OperationalRecord("product-1", "{}")))
            }
            override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> {
                calls.add("list:" + table)
                return listOf(OperationalRecord("product-1", "{}"))
            }
            override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
                assertEquals("tenant-a", scope.tenantId)
                assertEquals("business-a", scope.businessId)
                calls.add("save:" + table + ":" + id + ":" + revision)
            }
        }
        val vm = ItemsViewModel(fake)
        vm.save(scope, "product-1", "{}", 7)
        assertEquals(1, vm.load(scope).size)
        vm.observe(scope)
        assertEquals(listOf("save:products:product-1:7", "list:products", "observe:products"), calls)
    }
}
