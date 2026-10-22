package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 12C-04 composed interruption round: the production [SyncEngine] driving the
 * production [RoomSyncQueueStore] against a real (in-memory) Room database,
 * with only the transport scripted. The engine tests in core:sync use a fake
 * store and the Room tests in this module otherwise call the store directly;
 * this class closes the gap between them. It cannot live in core:sync:
 * core:store already depends on core:sync, so the reverse dependency would be
 * a cycle — and core:store's test classpath already sees both real classes.
 *
 * A pull can only fail "mid-apply" once the transport has returned, i.e.
 * inside the store's transaction; the failure is therefore injected as a
 * server batch whose second row the real store must reject (payload UUID
 * mismatch) after the first row has already been upserted inside the same
 * transaction. Transport-level failures happen before apply begins and are
 * covered by SyncEngineTest / AuthenticatedVpsTransportExchangeTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ComposedSyncEngineRoomTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    /** Records every batch the engine sends; replies via [responder]. */
    private class ScriptedTransport : SyncTransport {
        val batches = mutableListOf<SyncBatch>()
        var responder: (SyncBatch) -> SyncResponse =
            { SyncResponse(emptyList(), emptyList(), null) }

        override suspend fun exchange(batch: SyncBatch): SyncResponse {
            batches += batch
            return responder(batch)
        }
    }

    private fun seed(database: AppDatabase) {
        // Settings (cursor) and sync_queue rows have FKs to the tenant and
        // business parents; seed them first. Own scope, like the fixture
        // test's seed(), so callers may be plain or coroutine bodies.
        runBlocking {
            val now = System.currentTimeMillis()
            database.tenantDao().upsert(TenantEntity(
                id = identity.tenantId, name = "Test tenant",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
                syncStatus = "LOCAL",
            ))
            database.businessDao().upsert(BusinessEntity(
                id = identity.businessId, tenantId = identity.tenantId,
                name = "Test business", businessType = "RETAIL",
                currencyCode = "USD", timezoneId = "UTC",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
                syncStatus = "LOCAL",
            ))
        }
    }

    private fun openDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()

    private fun productChange(operationId: String, entityId: String, name: String, at: Long) =
        SyncChange(
            operationId, "products", entityId, OperationType.CREATE, 1,
            """{"uuid":"$entityId","name":"$name"}""", at,
        )

    /** A row the real store must reject mid-batch: payload UUID != entityId. */
    private fun poisonChange(at: Long) = SyncChange(
        "op-poison", "products", "product-poison", OperationType.CREATE, 1,
        """{"uuid":"someone-else","name":"Poison"}""", at,
    )

    @Test fun pullFailingMidBatchLeavesRecordsAndCursorUnchangedThroughEngine() = runBlocking {
        val db = openDatabase()
        try {
            seed(db)
            val queue = RoomSyncQueueStore(db)
            // Pre-existing local state: one stored record, cursor at "10".
            queue.applyRemoteAndSaveCursor(
                identity,
                listOf(productChange("op-existing", "product-existing", "Existing", 100L)),
                "10",
            )
            val transport = ScriptedTransport()
            transport.responder = {
                SyncResponse(
                    receipts = emptyList(),
                    remoteChanges = listOf(
                        productChange("op-new", "product-new", "New", 101L),
                        poisonChange(102L),
                    ),
                    nextCursor = "11",
                )
            }
            try {
                SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identity)
                fail("A batch the store rejects mid-apply must fail the whole round")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("UUID mismatch") == true)
            }
            // The composed path is atomic: the first (valid) row of the batch
            // was upserted inside the transaction and must be rolled back
            // with it; the pre-existing record and the cursor are untouched.
            assertEquals("10", queue.cursor(identity))
            assertNull(db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "products", "product-new",
            ))
            val existing = db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "products", "product-existing",
            )
            assertNotNull(existing)
            assertEquals("Existing", JSONObject(existing!!.payloadJson).getString("name"))
            assertEquals(1, RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
                .list(scope, "products").size)
            // The engine did run a real round: it offered the stored cursor.
            assertEquals("10", transport.batches.single().cursor)
        } finally { db.close() }
    }

    @Test fun successfulRoundAfterInterruptionAppliesFullBatchAndAdvancesCursorOnce() = runBlocking {
        val db = openDatabase()
        try {
            seed(db)
            val queue = RoomSyncQueueStore(db)
            queue.applyRemoteAndSaveCursor(identity, emptyList(), "10")
            val fullBatch = listOf(
                productChange("op-new-1", "product-new-1", "First", 101L),
                productChange("op-new-2", "product-new-2", "Second", 102L),
            )
            val transport = ScriptedTransport()
            // Round 1: the pull dies mid-apply (second row is poisoned).
            transport.responder = {
                SyncResponse(
                    receipts = emptyList(),
                    remoteChanges = listOf(fullBatch[0], poisonChange(102L)),
                    nextCursor = "11",
                )
            }
            try {
                SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identity)
                fail("Poisoned batch must fail the round")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("UUID mismatch") == true)
            }
            assertEquals("10", queue.cursor(identity))

            // Round 2: the server redelivers the complete, clean batch.
            transport.responder = { SyncResponse(emptyList(), fullBatch, "11") }
            val result = SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identity)
            assertEquals(2, result.downloaded)
            // The engine resumed from the preserved cursor, not a fresh one.
            assertEquals("10", transport.batches[1].cursor)
            assertEquals("11", queue.cursor(identity))
            for (change in fullBatch) {
                val stored = db.vpsBusinessRecordDao().get(
                    identity.tenantId, identity.businessId, "products", change.entityId,
                )
                assertNotNull("expected stored record for ${change.entityId}", stored)
                assertEquals(
                    JSONObject(change.payload).getString("name"),
                    JSONObject(stored!!.payloadJson).getString("name"),
                )
            }

            // Round 3: an exact redelivery of the same batch and cursor is an
            // idempotent no-op — records are not duplicated and the cursor
            // does not advance a second time.
            val replay = SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identity)
            assertEquals(2, replay.downloaded)
            assertEquals("11", queue.cursor(identity))
            assertEquals(2, RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
                .list(scope, "products").size)
        } finally { db.close() }
    }

    @Test fun pushAckedButCleanupInterruptedReplaysExactOperationIdNextRound() = runBlocking {
        val db = openDatabase()
        try {
            seed(db)
            val now = System.currentTimeMillis()
            db.syncQueueDao().enqueue(SyncQueueEntity(
                id = "op-push-1",
                tenantId = identity.tenantId,
                businessId = identity.businessId,
                entityType = "products",
                entityId = "product-push-1",
                operation = "CREATE",
                payloadJson = """{"uuid":"product-push-1","name":"Pushed"}""",
                revision = 1,
                status = "PENDING",
                attemptCount = 0,
                createdAt = now,
                updatedAt = now,
                deviceId = identity.deviceId,
            ))
            val queue = RoomSyncQueueStore(db)
            val transport = ScriptedTransport()
            // Round 1: the server ACCEPTS the push, but the same response
            // carries a pull batch that dies mid-apply. The engine persists
            // incoming changes before acknowledging outgoing ones, so the
            // round dies after the ack and before the local outbox cleanup
            // (markAccepted) — the exact crash window of a process death
            // between server ack and local cleanup.
            transport.responder = {
                SyncResponse(
                    receipts = listOf(SyncReceipt("op-push-1", SyncOutcome.ACCEPTED)),
                    remoteChanges = listOf(
                        productChange("op-remote-1", "product-remote-1", "Remote", 101L),
                        poisonChange(102L),
                    ),
                    nextCursor = "5",
                )
            }
            try {
                SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identity)
                fail("Poisoned pull batch must fail the round")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("UUID mismatch") == true)
            }
            // Nothing was cleaned up and nothing was lost: the operation is
            // still pending with its original attempt count, the incoming
            // record was rolled back, and the cursor never advanced.
            val interrupted = db.syncQueueDao().findById(
                identity.tenantId, identity.businessId, "op-push-1",
            )
            assertNotNull(interrupted)
            assertEquals("PENDING", interrupted!!.status)
            assertEquals(0, interrupted.attemptCount)
            assertEquals(listOf("op-push-1"), queue.pending(identity, 20).map { it.operationId })
            assertNull(queue.cursor(identity))
            assertNull(db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "products", "product-remote-1",
            ))

            // Round 2: the engine re-presents the EXACT same operation ID
            // (nothing re-minted, nothing dropped). The server already holds
            // the operation from round 1, so it answers DUPLICATE; the pull
            // batch is now clean and completes.
            transport.responder = {
                SyncResponse(
                    receipts = listOf(SyncReceipt("op-push-1", SyncOutcome.DUPLICATE)),
                    remoteChanges = listOf(
                        productChange("op-remote-1", "product-remote-1", "Remote", 101L),
                    ),
                    nextCursor = "5",
                )
            }
            val result = SyncEngine(queue, transport).run(StorageMode.CLOUD_SYNC, identity)
            assertEquals(1, result.uploaded)
            assertEquals(1, result.downloaded)
            assertEquals(0, result.retryable)
            assertEquals(listOf("op-push-1"), transport.batches[0].changes.map { it.operationId })
            assertEquals(listOf("op-push-1"), transport.batches[1].changes.map { it.operationId })
            // Exactly one queue row exists for the operation and it is now
            // SYNCED: not duplicated, not lost, not left pending.
            val settled = db.syncQueueDao().findById(
                identity.tenantId, identity.businessId, "op-push-1",
            )
            assertEquals("SYNCED", settled?.status)
            assertEquals(1, db.syncQueueDao().countByStatus(
                identity.tenantId, identity.businessId, "SYNCED",
            ))
            assertEquals(0, db.syncQueueDao().countByStatus(
                identity.tenantId, identity.businessId, "PENDING",
            ))
            assertTrue(queue.pending(identity, 20).isEmpty())
            assertEquals("5", queue.cursor(identity))
            assertNotNull(db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "products", "product-remote-1",
            ))
        } finally { db.close() }
    }
}
