package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SyncQueueEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.StorageMode
import com.lubrcapp.lubrcapp.core.sync.SyncBatch
import com.lubrcapp.lubrcapp.core.sync.SyncChange
import com.lubrcapp.lubrcapp.core.sync.SyncEngine
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import com.lubrcapp.lubrcapp.core.sync.SyncOutcome
import com.lubrcapp.lubrcapp.core.sync.SyncReceipt
import com.lubrcapp.lubrcapp.core.sync.SyncResponse
import com.lubrcapp.lubrcapp.core.sync.SyncTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 12C-08 residuals: a sync RUN is scoped. Two scopes share one database;
 * a run for one must not upload, acknowledge, stage or advance anything
 * belonging to the other — and after the user switches the active scope,
 * operating sync moves only the newly active scope's data.
 *
 * (Store-level per-scope record isolation and the scheduler's cancel
 * isolation were proven earlier; this class covers the composed run.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CrossScopeSyncIsolationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identityA = SyncIdentity("tenant-a", "business-a", "device-a")
    private val identityB = SyncIdentity("tenant-a", "business-b", "device-b")

    /** Acknowledges everything it is offered; replies via [responder]. */
    private class ScriptedTransport : SyncTransport {
        val batches = mutableListOf<SyncBatch>()
        var responder: (SyncBatch) -> SyncResponse = { batch ->
            SyncResponse(
                receipts = batch.changes.map {
                    SyncReceipt(it.operationId, SyncOutcome.ACCEPTED)
                },
                remoteChanges = emptyList(),
                nextCursor = "1",
            )
        }

        override suspend fun exchange(batch: SyncBatch): SyncResponse {
            batches += batch
            return responder(batch)
        }
    }

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().build()
        )
        context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun openDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()

    private fun seedScope(database: AppDatabase, identity: SyncIdentity) {
        runBlocking {
            val now = System.currentTimeMillis()
            database.tenantDao().upsert(TenantEntity(
                id = identity.tenantId, name = "Test tenant",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
                syncStatus = "LOCAL",
            ))
            database.businessDao().upsert(BusinessEntity(
                id = identity.businessId, tenantId = identity.tenantId,
                name = "Business ${identity.businessId}", businessType = "RETAIL",
                currencyCode = "USD", timezoneId = "UTC",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
                syncStatus = "LOCAL",
            ))
        }
    }

    private fun enqueuePending(
        database: AppDatabase,
        identity: SyncIdentity,
        operationId: String,
        entityId: String,
    ) {
        runBlocking {
            val now = System.currentTimeMillis()
            database.syncQueueDao().enqueue(SyncQueueEntity(
                id = operationId,
                tenantId = identity.tenantId,
                businessId = identity.businessId,
                entityType = "products",
                entityId = entityId,
                operation = "CREATE",
                payloadJson = """{"uuid":"$entityId","name":"Queued $entityId"}""",
                revision = 1,
                status = "PENDING",
                attemptCount = 0,
                createdAt = now,
                updatedAt = now,
                deviceId = identity.deviceId,
            ))
        }
    }

    private fun queueStatus(
        database: AppDatabase,
        identity: SyncIdentity,
        operationId: String,
    ): String? = runBlocking {
        database.syncQueueDao()
            .findById(identity.tenantId, identity.businessId, operationId)?.status
    }

    @Test fun syncRunForOneScopeLeavesOtherScopeUntouched() = runBlocking {
        val database = openDatabase()
        try {
            seedScope(database, identityA)
            seedScope(database, identityB)
            val queue = RoomSyncQueueStore(database)
            // Pre-state: B has a staged record and a cursor at 4; both
            // scopes have one pending operation each.
            queue.applyRemoteAndSaveCursor(identityB, listOf(SyncChange(
                "op-b-remote", "products", "product-b", OperationType.CREATE, 1,
                """{"uuid":"product-b","name":"B remote"}""", 100L,
            )), "4")
            enqueuePending(database, identityA, "op-a-1", "product-a")
            enqueuePending(database, identityB, "op-b-1", "product-b-local")

            // Run for scope A only.
            val transportA = ScriptedTransport()
            transportA.responder = { batch ->
                SyncResponse(
                    receipts = batch.changes.map {
                        SyncReceipt(it.operationId, SyncOutcome.ACCEPTED)
                    },
                    remoteChanges = listOf(SyncChange(
                        "op-a-remote", "products", "product-a-remote",
                        OperationType.CREATE, 1,
                        """{"uuid":"product-a-remote","name":"A remote"}""", 200L,
                    )),
                    nextCursor = "9",
                )
            }
            val resultA = SyncEngine(queue, transportA).run(StorageMode.CLOUD_SYNC, identityA)
            assertEquals(1, resultA.uploaded)
            assertEquals(1, resultA.downloaded)
            // The run only ever carried A's identity and A's operation.
            assertEquals(listOf(identityA), transportA.batches.map { it.identity })
            assertEquals(listOf("op-a-1"),
                transportA.batches.single().changes.map { it.operationId })
            assertEquals("SYNCED", queueStatus(database, identityA, "op-a-1"))
            assertEquals("9", queue.cursor(identityA))
            assertNotNull(database.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-a-remote"))
            // Scope B is exactly as before the run.
            assertEquals("PENDING", queueStatus(database, identityB, "op-b-1"))
            assertEquals("4", queue.cursor(identityB))
            val recordB = database.vpsBusinessRecordDao().get(
                "tenant-a", "business-b", "products", "product-b")
            assertNotNull(recordB)
            assertEquals("B remote", JSONObject(recordB!!.payloadJson).getString("name"))
            assertNull(database.vpsBusinessRecordDao().get(
                "tenant-a", "business-b", "products", "product-a-remote"))

            // The reverse run settles B and leaves A exactly as it was.
            val transportB = ScriptedTransport()
            transportB.responder = { batch ->
                SyncResponse(
                    receipts = batch.changes.map {
                        SyncReceipt(it.operationId, SyncOutcome.ACCEPTED)
                    },
                    remoteChanges = emptyList(),
                    nextCursor = "5",
                )
            }
            SyncEngine(queue, transportB).run(StorageMode.CLOUD_SYNC, identityB)
            assertEquals("SYNCED", queueStatus(database, identityB, "op-b-1"))
            assertEquals("5", queue.cursor(identityB))
            assertEquals("9", queue.cursor(identityA))
            assertNotNull(database.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-a-remote"))
        } finally { database.close() }
    }

    @Test fun switchThenOperateMovesOnlyNewScopeData() = runBlocking {
        val database = openDatabase()
        try {
            seedScope(database, identityA)
            seedScope(database, identityB)
            val queue = RoomSyncQueueStore(database)
            enqueuePending(database, identityA, "op-a-1", "product-a")
            enqueuePending(database, identityB, "op-b-1", "product-b")

            // Both scopes are scheduled for cloud sync; the user then
            // switches the active business from A to B and triggers sync.
            val scheduler = CloudSyncScheduler(context)
            scheduler.configure(identityA, StorageMode.CLOUD_SYNC,
                "https://vps.example", setOf("products"))
            scheduler.configure(identityB, StorageMode.CLOUD_SYNC,
                "https://vps.example", setOf("products"))
            scheduler.syncNow(identityB, StorageMode.CLOUD_SYNC,
                "https://vps.example", setOf("products"))
            val manager = WorkManager.getInstance(context)
            fun states(name: String) = manager.getWorkInfosForUniqueWork(name).get()
                .map { it.state }
            val jobA = "lubric-vps-sync:tenant-a:business-a:device-a"
            val jobB = "lubric-vps-sync:tenant-a:business-b:device-b"
            // The trigger enqueued only the new scope's one-time job, and
            // switching did not cancel the previous scope's schedule.
            assertTrue(states("$jobB:once").any { it == WorkInfo.State.ENQUEUED })
            assertTrue(states("$jobA:once").none { it == WorkInfo.State.ENQUEUED })
            assertTrue(states(jobA).any { it == WorkInfo.State.ENQUEUED })
            assertTrue(states(jobB).any { it == WorkInfo.State.ENQUEUED })

            // Operating sync under the new scope moves only B's data.
            val transport = ScriptedTransport()
            transport.responder = { batch ->
                SyncResponse(
                    receipts = batch.changes.map {
                        SyncReceipt(it.operationId, SyncOutcome.ACCEPTED)
                    },
                    remoteChanges = emptyList(),
                    nextCursor = "6",
                )
            }
            SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identityB)
            assertEquals(listOf(identityB), transport.batches.map { it.identity })
            assertEquals(listOf("op-b-1"),
                transport.batches.single().changes.map { it.operationId })
            assertEquals("SYNCED", queueStatus(database, identityB, "op-b-1"))
            assertEquals("6", queue.cursor(identityB))
            // The previous scope is undisturbed: its operation is still
            // queued and its cursor never moved.
            assertEquals("PENDING", queueStatus(database, identityA, "op-a-1"))
            assertNull(queue.cursor(identityA))
        } finally { database.close() }
    }
}
