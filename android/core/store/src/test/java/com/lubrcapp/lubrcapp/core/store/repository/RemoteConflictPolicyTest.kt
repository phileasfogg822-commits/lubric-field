package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.SyncChange
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 12C-05(b): the remote-change-vs-local-edit policy of the Android
 * receiving path, pinned as executable behavior.
 *
 * The full matrix across the segment's tests:
 * - local edit still PENDING/FAILED -> the remote change is refused and
 *   the whole pull batch rolls back (proven by
 *   OperationalBusinessRoomIntegrationTest.incomingRemoteChangeCannotOverwritePendingLocalEdit).
 * - local edit ALREADY SYNCED (acknowledged, no longer pending), editable
 *   table -> the remote change OVERWRITES the local staged record on
 *   arrival, even when the remote row's updated_at is strictly OLDER than
 *   the local edit's. RoomSyncQueueStore.applyRemoteAndSaveCursor performs
 *   no updated_at comparison anywhere; arrival order decides. (This class.)
 * - local posting ALREADY SYNCED, insert-only ledger table -> the local
 *   posting wins and the conflicting remote row is skipped while the
 *   cursor still advances (this class; the skip mechanism itself was
 *   proven in Package 3 for pull replays).
 *
 * Whether arrival-order-wins is the INTENDED policy for already-synced
 * editable records is an owner decision, flagged in the Package 8 gates
 * section: correctness today rests entirely on the server's push-side
 * LWW and pull ordering, because the receiving side has no guard of its
 * own once the local edit has been acknowledged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemoteConflictPolicyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")

    private fun openDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()

    private fun seedParents(database: AppDatabase) {
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

    /**
     * Saves a local edit through the production repository and settles it
     * as acknowledged, returning its operation ID: the exact state of an
     * edit that has already been synced when a conflicting remote change
     * later arrives.
     */
    private fun saveAndAcknowledge(
        database: AppDatabase,
        queue: RoomSyncQueueStore,
        table: String,
        entityId: String,
        payloadJson: String,
    ): String = runBlocking {
        val repository = RoomVpsBusinessRepository(database.vpsBusinessRecordDao(), database)
        repository.save(scope, table, entityId, payloadJson)
        val operationId = queue.pending(identity, 20).single().operationId
        queue.markAccepted(identity, setOf(operationId))
        assertTrue(queue.pending(identity, 20).isEmpty())
        operationId
    }

    @Test fun alreadySyncedLocalEditIsOverwrittenByOlderRemoteChange() = runBlocking {
        val database = openDatabase()
        try {
            seedParents(database)
            val queue = RoomSyncQueueStore(database)
            val operationId = saveAndAcknowledge(
                database, queue, "products", "product-1",
                """{"uuid":"product-1","name":"Local acked edit","updated_at":5000}""",
            )
            // A conflicting remote change arrives whose updated_at is
            // strictly older than the acknowledged local edit's.
            queue.applyRemoteAndSaveCursor(identity, listOf(SyncChange(
                "op-remote-stale", "products", "product-1", OperationType.UPDATE, 9,
                """{"uuid":"product-1","name":"Older remote edit","updated_at":1000}""", 900L,
            )), "4")
            // PINNED POLICY: the remote row wins on arrival. There is no
            // updated_at comparison on this path, so the older remote edit
            // silently replaces the newer acknowledged local edit in the
            // staged replica record.
            val stored = database.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-1")
            assertNotNull(stored)
            assertEquals("Older remote edit", JSONObject(stored!!.payloadJson).getString("name"))
            // The acknowledged queue row is untouched and the cursor advanced.
            assertEquals("SYNCED", database.syncQueueDao().findById(
                "tenant-a", "business-a", operationId)?.status)
            assertEquals("4", queue.cursor(identity))
        } finally { database.close() }
    }

    @Test fun alreadySyncedLedgerPostingIsNotOverwrittenByRemoteChange() = runBlocking {
        val database = openDatabase()
        try {
            seedParents(database)
            val queue = RoomSyncQueueStore(database)
            val operationId = saveAndAcknowledge(
                database, queue, "stock_movements", "move-1",
                """{"uuid":"move-1","description":"Local acked posting","reference":"REF-1"}""",
            )
            queue.applyRemoteAndSaveCursor(identity, listOf(SyncChange(
                "op-remote-conflict", "stock_movements", "move-1", OperationType.CREATE, 1,
                """{"uuid":"move-1","description":"Remote overwrite attempt","reference":"REF-9"}""",
                900L,
            )), "4")
            // Insert-only parity: the local posting wins; the conflicting
            // remote row is skipped, not applied and not an error, and the
            // cursor still advances past it.
            val stored = database.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "stock_movements", "move-1")
            assertNotNull(stored)
            assertEquals("Local acked posting",
                JSONObject(stored!!.payloadJson).getString("description"))
            assertEquals("REF-1", JSONObject(stored.payloadJson).getString("reference"))
            assertEquals("SYNCED", database.syncQueueDao().findById(
                "tenant-a", "business-a", operationId)?.status)
            assertEquals("4", queue.cursor(identity))
        } finally { database.close() }
    }
}
