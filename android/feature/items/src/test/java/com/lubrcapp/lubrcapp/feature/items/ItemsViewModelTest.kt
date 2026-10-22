package com.lubrcapp.lubrcapp.feature.items

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun deleteUsesScopedProductsRepository() = runBlocking {
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val calls = mutableListOf<String>()
        val fake = object : OperationalBusinessRepository {
            override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
                flowOf(emptyList())
            override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> = emptyList()
            override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) = Unit
            override suspend fun delete(scope: OperationalScope, table: String, id: String) {
                assertEquals("tenant-a", scope.tenantId)
                assertEquals("business-a", scope.businessId)
                assertEquals("device-a", scope.deviceId)
                calls.add("delete:" + table + ":" + id)
            }
        }
        val vm = ItemsViewModel(fake)
        vm.delete(scope, "product-1")
        assertEquals(listOf("delete:products:product-1"), calls)
    }

    @Test fun saveFailurePropagatesToCaller() {
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val fake = object : OperationalBusinessRepository {
            override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
                flowOf(emptyList())
            override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> = emptyList()
            override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
                throw IllegalStateException("storage unavailable")
            }
        }
        val vm = ItemsViewModel(fake)
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { vm.save(scope, "product-1", "{}") }
        }
        assertEquals("storage unavailable", thrown.message)
    }
}
