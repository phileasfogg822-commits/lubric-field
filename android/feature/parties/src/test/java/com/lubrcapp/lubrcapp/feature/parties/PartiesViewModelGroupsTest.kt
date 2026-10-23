package com.lubrcapp.lubrcapp.feature.parties

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

/** Phase 14.3: groups live in their own "customer_groups" table, decoded through the codec. */
class PartiesViewModelGroupsTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, List<OperationalRecord>>()
        val calls = mutableListOf<String>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> {
            calls.add("observe:$table")
            return flowOf(tables[table].orEmpty())
        }

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> {
            calls.add("list:$table")
            return tables[table].orEmpty()
        }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            calls.add("save:$table:$id")
            tables[table] = tables[table].orEmpty().filterNot { it.id == id } + OperationalRecord(id, json)
        }

        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            calls.add("delete:$table:$id")
            tables[table] = tables[table].orEmpty().filterNot { it.id == id }
        }
    }

    @Test fun groupCrudUsesCustomerGroupsTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = PartiesViewModel(fake)
        val group = PartyGroup(id = "g-1", name = "VIP", discountPercent = BigDecimal("15"))

        vm.saveGroup(scope, group)
        val loaded = vm.loadGroups(scope)
        assertEquals(listOf(group), loaded)
        assertEquals(group, vm.observeGroups(scope).first().single())

        vm.deleteGroup(scope, "g-1")
        assertEquals(emptyList<PartyGroup>(), vm.loadGroups(scope))
        assertEquals(
            listOf("save:customer_groups:g-1", "list:customer_groups", "observe:customer_groups", "delete:customer_groups:g-1", "list:customer_groups"),
            fake.calls,
        )
    }

    @Test fun legacyGroupPayloadDecodesTolerantly() = runBlocking {
        val fake = FakeRepository()
        fake.tables["customer_groups"] = listOf(OperationalRecord("g-9", """{"name":"Old Group"}"""))
        val vm = PartiesViewModel(fake)
        val groups = vm.loadGroups(scope)
        assertEquals(1, groups.size)
        assertEquals("Old Group", groups.single().name)
        assertEquals(null, groups.single().discountPercent)
    }
}
