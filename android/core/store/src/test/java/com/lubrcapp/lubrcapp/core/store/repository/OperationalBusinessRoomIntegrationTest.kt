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

    @Test fun offlineItemEditPreservesFieldsAndQueuesUpdateAcrossRestart() = runBlocking {
        val name = "offline-item-edit-test"
        context.deleteDatabase(name)
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val identity = SyncIdentity(scope.tenantId, scope.businessId, scope.deviceId)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        var db = open()
        try {
            val repository = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repository.save(scope, "products", "product-edit",
                """{"uuid":"product-edit","name":"Original","sku":"SKU-17","price":42}""")
            val edited = org.json.JSONObject(repository.list(scope, "products").single().json)
                .put("name", "Updated").toString()
            repository.save(scope, "products", "product-edit", edited)
            db.close()
            db = open()
            val reopened = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val item = org.json.JSONObject(reopened.list(scope, "products").single().json)
            assertEquals("Updated", item.getString("name"))
            assertEquals("SKU-17", item.getString("sku"))
            assertEquals(42, item.getInt("price"))
            val operations = RoomSyncQueueStore(db).pending(identity, 20)
            assertEquals(2, operations.size)
            assertEquals(listOf(OperationType.CREATE, OperationType.UPDATE),
                operations.map { it.operation })
            assertTrue(reopened.list(OperationalScope("tenant-b", "business-a", "device-b"),
                "products").isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun allOperationalModulesPersistOfflineEditsAcrossRestart() = runBlocking {
        val name = "operational-modules-edit-test"
        context.deleteDatabase(name)
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val identity = SyncIdentity(scope.tenantId, scope.businessId, scope.deviceId)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        var db = open()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val modules = listOf(
                "products" to "name", "stock_movements" to "description",
                "customers" to "name", "orders" to "description",
                "purchase_orders" to "description", "accounts" to "description"
            )
            modules.forEachIndexed { index, (table, field) ->
                val id = "entry-$index"
                repo.save(scope, table, id, org.json.JSONObject()
                    .put("uuid", id).put(field, "Before").put("reference", "REF-$index").toString())
                val edited = org.json.JSONObject(repo.list(scope, table).single().json)
                    .put(field, "After").toString()
                repo.save(scope, table, id, edited)
            }
            db.close()
            db = open()
            val restored = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            modules.forEachIndexed { index, (table, field) ->
                val row = org.json.JSONObject(restored.list(scope, table).single().json)
                assertEquals("After", row.getString(field))
                assertEquals("REF-$index", row.getString("reference"))
                assertTrue(restored.list(
                    OperationalScope("tenant-a", "business-other", "device-b"), table).isEmpty())
            }
            val queued = RoomSyncQueueStore(db).pending(identity, 100)
            assertEquals(12, queued.size)
            assertEquals(6, queued.count { it.operation == OperationType.CREATE })
            assertEquals(6, queued.count { it.operation == OperationType.UPDATE })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun unsupportedRemoteDeleteCannotEraseLocalDataOrAdvanceCursor() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val scope = OperationalScope("tenant-a", "business-a", "device-a")
            val identity = SyncIdentity(scope.tenantId, scope.businessId, scope.deviceId)
            val now = System.currentTimeMillis()
            db.tenantDao().upsert(TenantEntity(
                id = scope.tenantId, name = "Test tenant",
                createdAt = now, updatedAt = now, deviceId = scope.deviceId,
                syncStatus = "LOCAL"
            ))
            db.businessDao().upsert(BusinessEntity(
                id = scope.businessId, tenantId = scope.tenantId,
                name = "Test business", businessType = "RETAIL",
                currencyCode = "USD", timezoneId = "UTC",
                createdAt = now, updatedAt = now, deviceId = scope.deviceId,
                syncStatus = "LOCAL"
            ))
            val repository = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repository.save(scope, "products", "product-delete-guard",
                """{"uuid":"product-delete-guard","name":"Preserve me"}""")
            val queue = RoomSyncQueueStore(db)
            queue.applyRemoteAndSaveCursor(identity, emptyList(), "7")
            val deletion = SyncChange(
                "remote-delete", "products", "product-delete-guard",
                OperationType.DELETE, 2,
                """{"uuid":"product-delete-guard"}""", 100L
            )
            try {
                queue.applyRemoteAndSaveCursor(identity, listOf(deletion), "8")
                fail("Remote delete must not apply without a tombstone contract")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("tombstone") == true)
            }
            assertEquals("7", queue.cursor(identity))
            assertEquals("Preserve me", org.json.JSONObject(
                repository.list(scope, "products").single().json
            ).getString("name"))
            assertEquals(1, queue.pending(identity, 20).size)
        } finally {
            db.close()
        }
    }

    @Test fun rejectedIncomingBatchRollsBackRecordAndCursor() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val identity = SyncIdentity("tenant-a", "business-a", "device-a")
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
            queue.applyRemoteAndSaveCursor(identity, emptyList(), "3")
            val valid = SyncChange("op-valid", "products", "product-1",
                OperationType.CREATE, 1, """{"uuid":"product-1","name":"Valid"}""", 100L)
            val invalid = SyncChange("op-invalid", "products", "product-2",
                OperationType.CREATE, 1, """{"uuid":"wrong-id","name":"Invalid"}""", 101L)
            try {
                queue.applyRemoteAndSaveCursor(identity, listOf(valid, invalid), "4")
                fail("Invalid incoming record must reject the entire transaction")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("UUID mismatch") == true)
            }
            assertEquals("3", queue.cursor(identity))
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            assertTrue(repo.list(OperationalScope("tenant-a", "business-a", "device-a"), "products").isEmpty())
        } finally {
            db.close()
        }
    }


    @Test fun incomingRemoteChangeCannotOverwritePendingLocalEdit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val scope = OperationalScope("tenant-a", "business-a", "device-a")
            val identity = SyncIdentity(scope.tenantId, scope.businessId, scope.deviceId)
            val now = System.currentTimeMillis()
            db.tenantDao().upsert(TenantEntity(
                id = scope.tenantId, name = "Test tenant", createdAt = now,
                updatedAt = now, deviceId = scope.deviceId, syncStatus = "LOCAL"
            ))
            db.businessDao().upsert(BusinessEntity(
                id = scope.businessId, tenantId = scope.tenantId,
                name = "Test business", businessType = "RETAIL",
                currencyCode = "USD", timezoneId = "UTC",
                createdAt = now, updatedAt = now, deviceId = scope.deviceId,
                syncStatus = "LOCAL"
            ))
            val repository = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repository.save(scope, "products", "product-1",
                """{"uuid":"product-1","name":"Local edit"}""")
            val queue = RoomSyncQueueStore(db)
            queue.applyRemoteAndSaveCursor(identity, emptyList(), "2")
            val remote = SyncChange("remote-op", "products", "product-1",
                OperationType.UPDATE, 3,
                """{"uuid":"product-1","name":"Remote edit"}""", 200L)
            try {
                queue.applyRemoteAndSaveCursor(identity, listOf(remote), "3")
                fail("Pending local edit must block a conflicting remote overwrite")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("conflicts") == true)
            }
            assertEquals("2", queue.cursor(identity))
            assertEquals("Local edit", org.json.JSONObject(
                repository.list(scope, "products").single().json
            ).getString("name"))
            assertEquals(1, queue.pending(identity, 20).size)
        } finally {
            db.close()
        }
    }


    @Test fun repeatedIncomingBatchIsIdempotentAndCursorNeverRegresses() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val identity = SyncIdentity("tenant-a", "business-a", "device-a")
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
            val remote = SyncChange("op-1", "products", "product-1",
                OperationType.CREATE, 1,
                """{"uuid":"product-1","name":"Synced"}""", 100L)
            queue.applyRemoteAndSaveCursor(identity, listOf(remote), "9")
            queue.applyRemoteAndSaveCursor(identity, listOf(remote), "9")
            assertEquals("9", queue.cursor(identity))
            val scope = OperationalScope(identity.tenantId, identity.businessId, identity.deviceId)
            val repository = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            assertEquals(1, repository.list(scope, "products").size)
            try {
                queue.applyRemoteAndSaveCursor(identity, emptyList(), "8")
                fail("Older cursor must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("cursor") == true)
            }
            assertEquals("9", queue.cursor(identity))
            assertEquals(1, repository.list(scope, "products").size)
        } finally {
            db.close()
        }
    }

    @Test fun operationalTablesRemainIsolatedAcrossTenantsAndBusinesses() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val first = OperationalScope("tenant-a", "business-a", "device-a")
            val second = OperationalScope("tenant-a", "business-b", "device-b")
            val third = OperationalScope("tenant-b", "business-a", "device-c")
            repository.save(first, "products", "shared-id", """{"uuid":"shared-id","name":"First"}""")
            repository.save(second, "products", "shared-id", """{"uuid":"shared-id","name":"Second"}""")
            repository.save(third, "products", "shared-id", """{"uuid":"shared-id","name":"Third"}""")
            assertEquals("First", org.json.JSONObject(repository.list(first, "products").single().json).getString("name"))
            assertEquals("Second", org.json.JSONObject(repository.list(second, "products").single().json).getString("name"))
            assertEquals("Third", org.json.JSONObject(repository.list(third, "products").single().json).getString("name"))
            val queue = RoomSyncQueueStore(db)
            assertEquals(1, queue.pending(SyncIdentity(first.tenantId, first.businessId, first.deviceId), 20).size)
            assertEquals(1, queue.pending(SyncIdentity(second.tenantId, second.businessId, second.deviceId), 20).size)
            assertEquals(1, queue.pending(SyncIdentity(third.tenantId, third.businessId, third.deviceId), 20).size)
        } finally {
            db.close()
        }
    }


    @Test fun failedUploadPersistsRetryStateAcrossRestart() = runBlocking {
        val name = "sync-retry-persistence-test"
        context.deleteDatabase(name)
        val scope = OperationalScope("tenant-a", "business-a", "device-a")
        val identity = SyncIdentity(scope.tenantId, scope.businessId, scope.deviceId)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        var db = open()
        try {
            RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db).save(
                scope, "products", "product-retry",
                """{"uuid":"product-retry","name":"Offline retry"}"""
            )
            val queue = RoomSyncQueueStore(db)
            val operation = queue.pending(identity, 20).single()
            queue.markRetry(identity, setOf(operation.operationId))
            val retryRow = db.syncQueueDao().findById(
                identity.tenantId, identity.businessId, operation.operationId
            )
            assertEquals("FAILED", retryRow?.status)
            assertEquals(1, retryRow?.attemptCount)
            assertTrue((retryRow?.nextAttemptAt ?: 0L) > System.currentTimeMillis())
            db.close()
            db = open()
            val restored = db.syncQueueDao().findById(
                identity.tenantId, identity.businessId, operation.operationId
            )
            assertEquals("FAILED", restored?.status)
            assertEquals(1, restored?.attemptCount)
            assertTrue((restored?.nextAttemptAt ?: 0L) > 0L)
            assertTrue(RoomSyncQueueStore(db).pending(identity, 20).isEmpty())
            assertEquals(1, RoomVpsBusinessRepository(
                db.vpsBusinessRecordDao(), db
            ).list(scope, "products").size)
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
