package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessRemoteDataSource
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.business.Tenant
import com.lubrcapp.lubrcapp.domain.common.AuditMetadata
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.SoftDeleteMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRepositoriesTest {
    private val tenantId = TenantId("tenant-1")
    private val businessId = BusinessId("business-1")
    private val now = Instant.parse("2026-10-08T00:00:00Z")

    @Test
    fun saveBusinessIsLocalFirstAndDoesNotRequireRemote() = runBlocking {
        val local = FakeBusinessLocal()
        val remote = FakeBusinessRemote(RemoteResult.Unavailable("offline"))
        val repository = DefaultBusinessRepository(local, remote)
        val business = business()

        repository.saveBusiness(business)

        assertEquals(business, local.saved.single())
        assertEquals(0, remote.calls)
    }

    @Test
    fun refreshPersistsRemoteSnapshotIntoLocalSource() = runBlocking {
        val business = business()
        val local = FakeBusinessLocal()
        val remote = FakeBusinessRemote(RemoteResult.Success(listOf(business)))
        val repository = DefaultBusinessRepository(local, remote)

        val result = repository.refreshBusinesses(tenantId)

        assertEquals(RefreshResult.Updated(1), result)
        assertEquals(listOf(business), local.saved)
        assertEquals(1, remote.calls)
    }

    @Test
    fun unavailableRemoteIsReportedWithoutDestroyingLocalData() = runBlocking {
        val existing = business()
        val local = FakeBusinessLocal().apply { saved += existing }
        val remote = FakeBusinessRemote(RemoteResult.Unavailable("not configured"))
        val repository = DefaultBusinessRepository(local, remote)

        val result = repository.refreshBusinesses(tenantId)

        assertTrue(result is RefreshResult.Skipped)
        assertEquals(listOf(existing), local.saved)
    }

    private fun business() = Business(
        id = businessId,
        tenantId = tenantId,
        name = "Business",
        businessType = "OTHER",
        currency = CurrencyCode.of("PKR"),
        timezone = ZoneId.of("Asia/Karachi"),
        metadata = EntityMetadata(
            audit = AuditMetadata(now),
            deletion = SoftDeleteMetadata(),
            sync = SyncMetadata(
                deviceId = DeviceId("device-1"),
                revision = 0,
                status = SyncStatus.LOCAL_ONLY,
            ),
        ),
    )

    private class FakeBusinessLocal : BusinessLocalDataSource {
        val saved = mutableListOf<Business>()
        private val state = MutableStateFlow<List<Business>>(emptyList())

        override fun observeBusinesses(tenantId: TenantId): Flow<List<Business>> = state
        override suspend fun getBusiness(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Business? = saved.firstOrNull { it.id == businessId }

        override suspend fun saveTenant(tenant: Tenant) = Unit

        override suspend fun saveBusiness(business: Business) {
            saved.removeAll { it.id == business.id }
            saved += business
            state.value = saved.toList()
        }

        override suspend fun softDeleteBusiness(
            tenantId: TenantId,
            businessId: BusinessId,
            deletedAt: Instant,
            deletedBy: UserId?,
        ): Boolean = true
    }

    private class FakeBusinessRemote(
        private val result: RemoteResult<List<Business>>,
    ) : BusinessRemoteDataSource {
        var calls: Int = 0
        override suspend fun fetchBusinesses(tenantId: TenantId): RemoteResult<List<Business>> {
            calls += 1
            return result
        }
    }
}
