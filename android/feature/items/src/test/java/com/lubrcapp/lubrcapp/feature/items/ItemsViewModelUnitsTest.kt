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

/** Phase 16.1: units and conversions land on their own tables through their codecs. */
class ItemsViewModelUnitsTest {

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

    @Test fun unitCrudUsesUnitsTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val unit = ItemUnit(id = "u-1", name = "Carton")
        vm.saveUnit(scope, unit)
        assertEquals("units", fake.savedTable)
        assertEquals(listOf(unit), vm.loadUnits(scope))
        assertEquals(listOf(unit), vm.observeUnits(scope).first())

        vm.deleteUnit(scope, "u-1")
        assertEquals("units", fake.deletedTable)
        assertEquals(emptyList<ItemUnit>(), vm.loadUnits(scope))
    }

    @Test fun conversionCrudUsesConversionsTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val conversion = UnitConversion(id = "cv-1", fromUnitId = "u-carton", toUnitId = "u-bottle", factor = BigDecimal("12"))
        vm.saveConversion(scope, conversion)
        assertEquals("unit_conversions", fake.savedTable)
        assertEquals(listOf(conversion), vm.loadConversions(scope))
        assertEquals(listOf(conversion), vm.observeConversions(scope).first())

        vm.deleteConversion(scope, "cv-1")
        assertEquals("unit_conversions", fake.deletedTable)
        assertEquals(emptyList<UnitConversion>(), vm.loadConversions(scope))
    }

    @Test fun legacyUnitPayloadDecodesThroughViewModel() = runBlocking {
        val fake = FakeRepository()
        fake.tables["units"] = mutableListOf(OperationalRecord("u-7", """{"uuid":"u1","name":"Legacy Unit"}"""))
        val vm = ItemsViewModel(fake)
        assertEquals("Legacy Unit", vm.loadUnits(scope).single().name)
    }
}
