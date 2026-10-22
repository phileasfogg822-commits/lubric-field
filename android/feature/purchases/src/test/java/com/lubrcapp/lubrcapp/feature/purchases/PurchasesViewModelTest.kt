package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchasesViewModelTest {
    @Test fun saveLoadAndObserveUseCorrectScopedRepository() = runBlocking {
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val calls = mutableListOf<String>()
        val fake = object : OperationalBusinessRepository {
            override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> {
                calls.add("observe:" + table)
                return flowOf(listOf(OperationalRecord("record-1", "{}")))
            }
            override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> {
                calls.add("list:" + table)
                return listOf(OperationalRecord("record-1", "{}"))
            }
            override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
                assertEquals("tenant-a", scope.tenantId)
                assertEquals("business-a", scope.businessId)
                assertEquals("device-a", scope.deviceId)
                calls.add("save:" + table + ":" + id + ":" + revision)
            }
        }
        val vm = PurchasesViewModel(fake)
        vm.save(scope, "record-1", "{}", 7)
        assertEquals(1, vm.load(scope).size)
        vm.observe(scope)
        assertEquals(listOf("save:purchase_orders:record-1:7", "list:purchase_orders", "observe:purchase_orders"), calls)
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
        val vm = PurchasesViewModel(fake)
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { vm.save(scope, "record-1", "{}") }
        }
        assertEquals("storage unavailable", thrown.message)
    }
}

