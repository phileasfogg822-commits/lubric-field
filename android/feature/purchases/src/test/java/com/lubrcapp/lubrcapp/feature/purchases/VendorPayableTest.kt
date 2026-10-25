package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorPayableTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(tables[table]?.toList() ?: emptyList())
        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            tables[table]?.toList() ?: emptyList()
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            val rows = tables.getOrPut(table) { mutableListOf() }
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    private fun payable() = VendorPayableRecord(
        id = "po-1",
        vendorId = "vendor-1",
        documentNumber = "PINV-2026-00001",
        status = "RECEIVED",
        totalAmount = BigDecimal("50000"),
        paidAmount = BigDecimal("20000"),
        currency = "PKR",
        date = "2026-10-25",
        dueDate = "2026-11-25",
        notes = "Partial delivery",
        createdAt = "2026-10-25T00:00:00Z",
    )

    @Test fun codecRoundTripPreservesEveryField() {
        val decoded = VendorPayableJson.decode("po-1", VendorPayableJson.encode(payable()))
        assertEquals("po-1", decoded.id)
        assertEquals("vendor-1", decoded.vendorId)
        assertEquals("PINV-2026-00001", decoded.documentNumber)
        assertEquals("RECEIVED", decoded.status)
        assertEquals(0, BigDecimal("50000").compareTo(decoded.totalAmount))
        assertEquals(0, BigDecimal("20000").compareTo(decoded.paidAmount))
        assertEquals("PKR", decoded.currency)
        assertEquals("2026-10-25", decoded.date)
        assertEquals("2026-11-25", decoded.dueDate)
        assertEquals("Partial delivery", decoded.notes)
        assertEquals("2026-10-25T00:00:00Z", decoded.createdAt)
        assertEquals(0, BigDecimal("30000").compareTo(decoded.balanceDue))
    }

    @Test fun codecToleratesMissingUnknownAndBadFields() {
        val empty = VendorPayableJson.decode("po-2", "{}")
        assertEquals("DRAFT", empty.status)
        assertEquals(0, BigDecimal.ZERO.compareTo(empty.totalAmount))
        assertEquals(0, BigDecimal.ZERO.compareTo(empty.paidAmount))
        assertEquals("", empty.vendorId)

        val garbage = VendorPayableJson.decode("po-3", "not json at all")
        assertEquals("po-3", garbage.id)
        assertEquals("DRAFT", garbage.status)

        val badAmount = VendorPayableJson.decode(
            "po-4",
            """{"totalAmount":"not-a-number","paidAmount":"100","status":"PAID","surprise":"ignored"}""",
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(badAmount.totalAmount))
        assertEquals(0, BigDecimal("100").compareTo(badAmount.paidAmount))
        assertEquals("PAID", badAmount.status)
    }

    @Test fun viewModelSaveLoadAndLookupRoundTrip() = runBlocking {
        val vm = PurchasesViewModel(FakeRepository())
        vm.savePayable(scope, payable())

        val all = vm.loadPayables(scope)
        assertEquals(1, all.size)
        assertEquals("po-1", all[0].id)
        assertEquals(0, BigDecimal("30000").compareTo(all[0].balanceDue))

        val found = vm.loadPayableForPurchase(scope, "po-1")
        assertEquals("PINV-2026-00001", found?.documentNumber)
        assertNull(vm.loadPayableForPurchase(scope, "po-missing"))
    }

    @Test fun resaveOverwritesSamePayable() = runBlocking {
        val vm = PurchasesViewModel(FakeRepository())
        vm.savePayable(scope, payable())
        vm.savePayable(scope, payable().copy(status = "PAID", paidAmount = BigDecimal("50000")))

        val all = vm.loadPayables(scope)
        assertEquals(1, all.size)
        assertEquals("PAID", all[0].status)
        assertEquals(0, BigDecimal.ZERO.compareTo(all[0].balanceDue))
    }
}
