package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 14.1: the ViewModel's typed surface decodes/encodes through PartyJson. */
class PartiesViewModelTypedTest {

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

    @Test fun loadPartiesDecodesLegacyAndTypedRecords() = runBlocking {
        val legacy = OperationalRecord("p-1", """{"uuid":"u1","name":"Legacy Name"}""")
        val typedParty = Party(
            id = "p-2",
            name = "Typed Name",
            type = PartyType.SUPPLIER,
            openingBalance = BigDecimal("42.50"),
        )
        val typed = OperationalRecord("p-2", PartyJson.encode(typedParty))
        val vm = PartiesViewModel(FakeRepository(listOf(legacy, typed)))

        val parties = vm.loadParties(scope)
        assertEquals(2, parties.size)
        assertEquals("Legacy Name", parties[0].name)
        assertEquals(PartyType.CUSTOMER, parties[0].type)
        assertEquals(typedParty, parties[1])
    }

    @Test fun observePartiesMapsThroughCodec() = runBlocking {
        val record = OperationalRecord("p-1", """{"name":"Observed","description":"note"}""")
        val vm = PartiesViewModel(FakeRepository(listOf(record)))
        val parties = vm.observeParties(scope).first()
        assertEquals(1, parties.size)
        assertEquals("Observed", parties.single().name)
        assertEquals("note", parties.single().notes)
    }

    @Test fun savePartyEncodesThroughCodecToCustomersTable() = runBlocking {
        val fake = FakeRepository(emptyList())
        val vm = PartiesViewModel(fake)
        val party = Party(
            id = "p-7",
            name = "Saved Party",
            type = PartyType.BOTH,
            creditLimit = BigDecimal("5000"),
        )
        vm.saveParty(scope, party)
        assertEquals("customers", fake.savedTable)
        assertEquals("p-7", fake.savedId)
        assertEquals(party, PartyJson.decode("p-7", fake.savedJson!!))
    }
}
