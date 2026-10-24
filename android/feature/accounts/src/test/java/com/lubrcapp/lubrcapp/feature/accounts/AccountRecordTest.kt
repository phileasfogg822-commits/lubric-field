package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRecordTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeAccountRepository : OperationalBusinessRepository {
        val rows = mutableListOf<OperationalRecord>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(rows.toList())

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            rows.toList()

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    @Test
    fun codecRoundTrips() {
        val account = AccountRecord(
            id = "acc-1",
            code = "1000",
            name = "Cash",
            type = AccountType.ASSET,
            createdAt = "2026-10-24T00:00:00Z",
        )
        assertEquals(account, AccountJson.decode(account.id, AccountJson.encode(account)))
    }

    @Test
    fun codecToleratesGarbageAndUnknownType() {
        val garbage = AccountJson.decode("acc-9", "{broken")
        assertEquals("acc-9", garbage.id)
        assertEquals(AccountType.OTHER_INCOME, garbage.type)

        val unknown = AccountJson.decode("acc-9", """{"code":"9000","name":"Mystery","type":"NOPE"}""")
        assertEquals(AccountType.OTHER_INCOME, unknown.type)
        assertEquals("Mystery", unknown.name)
    }

    @Test
    fun codecFallsBackToLegacyDescriptionForName() {
        val legacy = AccountJson.decode("acc-9", """{"description":"Old petty cash"}""")
        assertEquals("Old petty cash", legacy.name)
        assertEquals(AccountType.OTHER_INCOME, legacy.type)
    }

    @Test
    fun domainBridgeMapsFields() {
        val account = AccountRecord(
            id = "acc-1",
            code = "1000",
            name = "Cash",
            type = AccountType.ASSET,
            parentId = "acc-root",
            createdAt = "2026-10-24T00:00:00Z",
        )
        val domain = account.toDomainAccount(
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-a"),
            deviceId = DeviceId("device-a"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
        )
        assertEquals("acc-1", domain.id.value)
        assertEquals("1000", domain.code)
        assertEquals("Cash", domain.name)
        assertEquals(AccountType.ASSET, domain.type)
        assertEquals("acc-root", domain.parentId?.value)
        assertTrue(domain.isActive)
    }

    @Test
    fun saveLoadAndDeactivateRoundTrip() = runBlocking {
        val fake = FakeAccountRepository()
        val vm = AccountsViewModel(fake)
        val account = AccountRecord.of("1000", "Cash", AccountType.ASSET)
        vm.saveAccount(scope, account)
        assertEquals(listOf(account), vm.loadAccounts(scope))
        assertEquals(listOf(account), vm.loadActiveAccounts(scope))
        vm.deactivateAccount(scope, account.id)
        assertTrue(vm.loadAccounts(scope).single().isActive.not())
        assertEquals(emptyList<AccountRecord>(), vm.loadActiveAccounts(scope))
    }

    @Test
    fun loadAccountsSortsByCodeThenName() = runBlocking {
        val fake = FakeAccountRepository()
        val vm = AccountsViewModel(fake)
        val b = AccountRecord("b", "2000", "Bank", AccountType.ASSET, null, true, "")
        val a = AccountRecord("a", "1000", "Cash", AccountType.ASSET, null, true, "")
        val a2 = AccountRecord("a2", "1000", "Coin", AccountType.ASSET, null, true, "")
        vm.saveAccount(scope, b)
        vm.saveAccount(scope, a2)
        vm.saveAccount(scope, a)
        assertEquals(listOf(a, a2, b), vm.loadAccounts(scope))
    }
}
