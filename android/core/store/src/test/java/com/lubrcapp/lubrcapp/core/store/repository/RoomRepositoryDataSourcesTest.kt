package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.core.repository.SyncOperationKind
import com.lubrcapp.lubrcapp.core.repository.SyncQueueItem
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.business.Tenant
import com.lubrcapp.lubrcapp.domain.common.AuditMetadata
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.SettingId
import com.lubrcapp.lubrcapp.domain.common.SoftDeleteMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncOperationId
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomRepositoryDataSourcesTest {
    private lateinit var database: AppDatabase
    private val tenantId = TenantId("tenant-a")
    private val businessId = BusinessId("business-a")
    private val otherBusinessId = BusinessId("business-b")
    private val now = Instant.parse("2026-10-08T00:00:00Z")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = database.close()

    @Test
    fun businessRoundTripsThroughRepositoryDataSource() = runBlocking {
        val source = RoomBusinessLocalDataSource(database.tenantDao(), database.businessDao())
        source.saveTenant(tenant())
        source.saveBusiness(business(businessId, "Alpha"))

        assertEquals(
            business(businessId, "Alpha"),
            source.getBusiness(tenantId, businessId),
        )
        assertEquals(
            listOf("Alpha"),
            source.observeBusinesses(tenantId).first().map { it.name },
        )
    }

    @Test
    fun settingsRemainBusinessScopedAndSoftDeleteAware() = runBlocking {
        val businessSource = RoomBusinessLocalDataSource(database.tenantDao(), database.businessDao())
        businessSource.saveTenant(tenant())
        businessSource.saveBusiness(business(businessId, "Alpha"))
        businessSource.saveBusiness(business(otherBusinessId, "Beta"))

        val source = RoomSettingsLocalDataSource(database.settingsDao())
        source.saveSetting(setting("s-a", businessId, "A-"))
        source.saveSetting(setting("s-b", otherBusinessId, "B-"))

        assertEquals("A-", source.getSetting(tenantId, businessId, "invoice.prefix")?.value)
        assertEquals("B-", source.getSetting(tenantId, otherBusinessId, "invoice.prefix")?.value)

        source.softDeleteSetting(tenantId, businessId, "invoice.prefix", now.plusSeconds(1), null)
        assertNull(source.getSetting(tenantId, businessId, "invoice.prefix"))
        assertEquals("B-", source.getSetting(tenantId, otherBusinessId, "invoice.prefix")?.value)
    }

    @Test
    fun syncQueueContractUsesTypedDomainValues() = runBlocking {
        val source = RoomSyncQueueLocalDataSource(database.syncQueueDao())
        val item = SyncQueueItem(
            id = SyncOperationId("sync-1"),
            tenantId = tenantId,
            businessId = businessId,
            entityType = "BUSINESS",
            entityId = businessId.value,
            operation = SyncOperationKind.UPDATE,
            status = SyncStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            deviceId = "device-1",
        )

        source.enqueue(item)

        assertEquals(
            listOf(item),
            source.nextBatch(
                tenantId,
                businessId,
                setOf(SyncStatus.PENDING),
                now.plusSeconds(10),
                10,
            ),
        )
        assertEquals(1, source.getSummary(tenantId, businessId).pending)
    }

    private fun metadata() = EntityMetadata(
        audit = AuditMetadata(now),
        deletion = SoftDeleteMetadata(),
        sync = SyncMetadata(
            deviceId = DeviceId("device-1"),
            revision = 0,
            status = SyncStatus.LOCAL_ONLY,
        ),
    )

    private fun tenant() = Tenant(
        id = tenantId,
        name = "Tenant",
        metadata = metadata(),
    )

    private fun business(id: BusinessId, name: String) = Business(
        id = id,
        tenantId = tenantId,
        name = name,
        businessType = "OTHER",
        currency = CurrencyCode.of("PKR"),
        timezone = ZoneId.of("Asia/Karachi"),
        metadata = metadata(),
    )

    private fun setting(id: String, businessId: BusinessId, value: String) = BusinessSetting(
        id = SettingId(id),
        tenantId = tenantId,
        businessId = businessId,
        key = "invoice.prefix",
        value = value,
        metadata = metadata(),
    )
}
