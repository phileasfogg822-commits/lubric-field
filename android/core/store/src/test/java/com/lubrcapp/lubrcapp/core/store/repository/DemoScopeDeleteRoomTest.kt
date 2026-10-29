package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SyncQueueEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 32.3 — the demo wipe: one transaction removes every row of a
 * demo scope (records, business, tenant, memberships, settings, sync
 * queue) and leaves a real scope untouched. Non-demo scopes are
 * refused outright.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DemoScopeDeleteRoomTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun inMemoryDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries().build()

    private suspend fun seedScope(
        db: AppDatabase,
        repo: RoomVpsBusinessRepository,
        tenantId: String,
        businessId: String,
    ) {
        db.tenantDao().upsert(
            TenantEntity(
                id = tenantId, name = "Tenant $tenantId",
                createdAt = 1L, updatedAt = 1L, deviceId = "device-a", syncStatus = "LOCAL_ONLY",
            ),
        )
        db.businessDao().upsert(
            BusinessEntity(
                id = businessId, tenantId = tenantId, name = "Business $businessId",
                businessType = "General Store", currencyCode = "PKR", timezoneId = "Asia/Karachi",
                createdAt = 1L, updatedAt = 1L, deviceId = "device-a", syncStatus = "LOCAL_ONLY",
            ),
        )
        db.accessDao().upsertUserBusiness(
            UserBusinessEntity(
                id = "membership-$businessId", tenantId = tenantId, businessId = businessId,
                userId = "user-1", createdAt = 1L, updatedAt = 1L,
                deviceId = "device-a", syncStatus = "LOCAL_ONLY",
            ),
        )
        db.settingsDao().upsert(
            SettingEntity(
                id = "setting-$businessId", tenantId = tenantId, businessId = businessId,
                settingKey = "business.owner_user_id", value = "user-1",
                createdAt = 1L, updatedAt = 1L, deviceId = "device-a", syncStatus = "LOCAL_ONLY",
            ),
        )
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                id = "sync-$businessId", tenantId = tenantId, businessId = businessId,
                entityType = "products", entityId = "p-1", operation = "CREATE",
                createdAt = 1L, updatedAt = 1L, deviceId = "device-a",
            ),
        )
        val scope = OperationalScope(tenantId, businessId, "device-a")
        repo.save(scope, "products", "p-1", """{"uuid":"p-1","name":"Demo item"}""")
        repo.save(scope, "journal_entries", "je-1", """{"uuid":"je-1"}""")
    }

    @Test fun wipeRemovesDemoScopeAndLeavesRealScopeIntact() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            seedScope(db, repo, "demo-tenant-1", "demo-business-1")
            seedScope(db, repo, "tenant-real", "business-real")

            repo.deleteBusinessScope("demo-tenant-1", "demo-business-1")

            val demo = OperationalScope("demo-tenant-1", "demo-business-1", "device-a")
            assertTrue(repo.list(demo, "products").isEmpty())
            assertTrue(repo.list(demo, "journal_entries").isEmpty())
            assertNull(db.businessDao().getById("demo-tenant-1", "demo-business-1"))
            assertNull(db.accessDao().getMembership("demo-tenant-1", "demo-business-1", "user-1"))
            assertNull(db.settingsDao().get("demo-tenant-1", "demo-business-1", "business.owner_user_id"))
            assertNull(db.syncQueueDao().findById("demo-tenant-1", "demo-business-1", "sync-demo-business-1"))

            val real = OperationalScope("tenant-real", "business-real", "device-a")
            assertEquals(listOf("p-1"), repo.list(real, "products").map { it.id })
            assertEquals(listOf("je-1"), repo.list(real, "journal_entries").map { it.id })
            assertTrue(db.businessDao().getById("tenant-real", "business-real") != null)
            assertTrue(db.accessDao().getMembership("tenant-real", "business-real", "user-1") != null)
        } finally {
            db.close()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun wipeRefusesRealScope() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.deleteBusinessScope("tenant-real", "business-real")
        } finally {
            db.close()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun wipeRefusesMixedScope() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.deleteBusinessScope("demo-tenant-1", "business-real")
        } finally {
            db.close()
        }
    }
}
