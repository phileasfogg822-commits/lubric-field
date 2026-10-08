package com.lubrcapp.lubrcapp.core.store.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SyncQueueEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun businessQueriesAreTenantScopedAndSoftDeleteAware() = runBlocking {
        val now = 1_000L
        database.tenantDao().upsert(tenant("tenant-a", now))
        database.businessDao().upsert(business("tenant-a", "business-a", "Alpha", now))
        database.businessDao().upsert(business("tenant-a", "business-b", "Beta", now))

        database.settingsDao().upsert(
            setting("setting-a", "tenant-a", "business-a", "invoice.prefix", "A-", now)
        )
        database.settingsDao().upsert(
            setting("setting-b", "tenant-a", "business-b", "invoice.prefix", "B-", now)
        )

        assertEquals(
            "A-",
            database.settingsDao().get("tenant-a", "business-a", "invoice.prefix")?.value,
        )
        assertEquals(
            "B-",
            database.settingsDao().get("tenant-a", "business-b", "invoice.prefix")?.value,
        )

        database.businessDao().softDelete("tenant-a", "business-a", 2_000L, "user-1")

        assertNull(database.businessDao().getById("tenant-a", "business-a"))
        assertNotNull(database.businessDao().getById("tenant-a", "business-b"))
    }

    @Test
    fun transactionRollsBackAllWritesOnFailure() = runBlocking {
        database.tenantDao().upsert(tenant("tenant-a", 1_000L))

        try {
            database.withTransaction {
                database.businessDao().upsert(
                    business("tenant-a", "business-a", "Alpha", 1_000L)
                )
                error("force rollback")
            }
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertNull(database.businessDao().getById("tenant-a", "business-a"))
    }

    @Test
    fun syncQueueIsScopedAndOldestEligibleOperationComesFirst() = runBlocking {
        database.tenantDao().upsert(tenant("tenant-a", 1_000L))
        database.businessDao().upsert(business("tenant-a", "business-a", "Alpha", 1_000L))
        database.businessDao().upsert(business("tenant-a", "business-b", "Beta", 1_000L))

        database.syncQueueDao().enqueue(
            queue("q-later", "tenant-a", "business-a", createdAt = 2_000L)
        )
        database.syncQueueDao().enqueue(
            queue("q-first", "tenant-a", "business-a", createdAt = 1_000L)
        )
        database.syncQueueDao().enqueue(
            queue("q-other", "tenant-a", "business-b", createdAt = 500L)
        )

        val batch = database.syncQueueDao().nextBatch(
            tenantId = "tenant-a",
            businessId = "business-a",
            statuses = listOf("PENDING", "FAILED"),
            now = 5_000L,
            limit = 10,
        )

        assertEquals(listOf("q-first", "q-later"), batch.map { it.id })
    }

    private fun tenant(id: String, now: Long) = TenantEntity(
        id = id,
        name = id,
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun business(
        tenantId: String,
        id: String,
        name: String,
        now: Long,
    ) = BusinessEntity(
        id = id,
        tenantId = tenantId,
        name = name,
        businessType = "OTHER",
        currencyCode = "PKR",
        timezoneId = "Asia/Karachi",
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun setting(
        id: String,
        tenantId: String,
        businessId: String,
        key: String,
        value: String,
        now: Long,
    ) = SettingEntity(
        id = id,
        tenantId = tenantId,
        businessId = businessId,
        settingKey = key,
        value = value,
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun queue(
        id: String,
        tenantId: String,
        businessId: String,
        createdAt: Long,
    ) = SyncQueueEntity(
        id = id,
        tenantId = tenantId,
        businessId = businessId,
        entityType = "BUSINESS",
        entityId = businessId,
        operation = "UPDATE",
        createdAt = createdAt,
        updatedAt = createdAt,
        deviceId = "device-1",
    )
}
