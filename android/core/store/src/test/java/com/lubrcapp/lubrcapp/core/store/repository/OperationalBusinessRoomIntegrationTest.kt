package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.SyncChange
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OperationalBusinessRoomIntegrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun offlineWriteSurvivesDatabaseRestartAndQueuesUpload() = runBlocking {
        val name = "offline-operational-test"
        context.deleteDatabase(name)
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        var db = open()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.save(scope, "customers", "customer-1", """{"uuid":"customer-1","name":"Offline"}""")
            assertEquals("Offline", org.json.JSONObject(repo.list(scope, "customers").single().json).getString("name"))
            db.close()
            db = open()
            val reopened = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            assertEquals(1, reopened.list(scope, "customers").size)
            val queued = RoomSyncQueueStore(db).pending(SyncIdentity("tenant-a", "business-a", "device-a"), 20)
            assertEquals(1, queued.size)
            assertEquals(OperationType.CREATE, queued.single().operation)
            assertTrue(RoomSyncQueueStore(db).pending(SyncIdentity("tenant-a", "business-a", "device-b"), 20).isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun incomingRecordAndCursorAreCommittedTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val identity = SyncIdentity("tenant-a", "business-a", "device-a")
            // Settings rows require existing tenant and business parents.
            val now = System.currentTimeMillis()
            db.tenantDao().upsert(TenantEntity(
                id = identity.tenantId, name = "Test tenant",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
                syncStatus = "LOCAL"
            ))
            db.businessDao().upsert(BusinessEntity(
                id = identity.businessId, tenantId = identity.tenantId,
                name = "Test business", businessType = "RETAIL",
                currencyCode = "USD", timezoneId = "UTC",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
                syncStatus = "LOCAL"
            ))
            val queue = RoomSyncQueueStore(db)
            queue.applyRemoteAndSaveCursor(identity, listOf(
                SyncChange("op-remote", "products", "product-1", OperationType.CREATE, 1,
                    """{"uuid":"product-1","name":"Remote"}""", 100L)
            ), "7")
            assertEquals("7", queue.cursor(identity))
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            assertEquals(1, repo.list(OperationalScope("tenant-a", "business-a", "device-a"), "products").size)
            assertTrue(repo.list(OperationalScope("tenant-b", "business-a", "device-a"), "products").isEmpty())
        } finally { db.close() }
    }
}
