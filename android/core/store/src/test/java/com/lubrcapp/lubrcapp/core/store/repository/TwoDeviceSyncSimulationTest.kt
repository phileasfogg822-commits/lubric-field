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
import com.lubrcapp.lubrcapp.core.sync.SyncContract
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
 * 12C-05 two-device simulation: two independent Room databases and two
 * [SyncEngine] instances with distinct device identities, exchanging through
 * one shared in-memory stand-in for the VPS ([InMemoryVpsServer]) whose
 * behavior mirrors the production push/pull handler in server/index.js.
 *
 * The receiving legs are proven end to end (server state -> engine -> real
 * RoomSyncQueueStore), and since Package 9 the sending leg settles end to
 * end as well: see [pushRoundSettlesWhenPullEchoesOwnChange], which pinned
 * the 12C-05 push-settlement defect in Package 8 and was inverted when the
 * same-response-ack exemption fixed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TwoDeviceSyncSimulationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identityA = SyncIdentity("tenant-a", "business-a", "device-a")
    private val identityB = SyncIdentity("tenant-a", "business-a", "device-b")
    private val identityC = SyncIdentity("tenant-a", "business-b", "device-c")

    /**
     * In-memory stand-in for the production VPS handler (server/index.js),
     * modeled at the [SyncTransport] seam the engine actually consumes.
     *
     * Push semantics mirrored from the handler:
     * - every change gets exactly one receipt, keyed by its exact operation ID;
     * - unknown tables and malformed changes are rejected;
     * - editable tables are last-writer-wins by the row's updated_at: an
     *   incoming row older than the stored one is rejected, equal or newer
     *   is applied;
     * - the four ledger tables (account_transactions, cash_movements,
     *   stock_movements, party_ledger) are insert-only: a replay whose
     *   business fields match the stored posting is an idempotent accept
     *   (no state change), a differing one is rejected;
     * - delete applies only to the handler's soft-delete tables and only
     *   when the row exists in the shop.
     *
     * Pull semantics mirrored from the handler and AuthenticatedVpsTransport:
     * - state is kept per shop (tenant + business); a pull returns the
     *   CURRENT state of every row written after the caller's cursor,
     *   including rows the caller itself pushed (the production pull has no
     *   origin-device exclusion) as UPDATE changes;
     * - the cursor is a monotonic server stamp (the handler returns its
     *   server time; the double uses a logical clock with the same
     *   monotonic, advance-on-write behavior).
     *
     * One deliberate abstraction: the handler stores a single updated_at
     * column used both for LWW and for pull visibility, stamping NOW() when
     * the client row carries none (Android payloads never carry one, so in
     * production both roles collapse to the server stamp). The double keeps
     * the two roles separate — payload updated_at drives LWW (desktop rows
     * carry one), the logical clock drives pull visibility — so both
     * behaviors stay deterministic under test.
     */
    private class InMemoryVpsServer : SyncTransport {
        private data class StoredRow(
            val payloadJson: String,
            val lwwUpdatedAt: Long,
            val serverStamp: Long,
            val deleted: Boolean,
        )

        private class Shop {
            val rows = LinkedHashMap<Pair<String, String>, StoredRow>()
            var clock = 0L
        }

        private val shops = mutableMapOf<Pair<String, String>, Shop>()
        val exchanges = mutableListOf<SyncBatch>()

        override suspend fun exchange(batch: SyncBatch): SyncResponse {
            SyncContract.validateBatch(batch)
            exchanges += batch
            val shop = shops.getOrPut(batch.identity.tenantId to batch.identity.businessId) { Shop() }
            val receipts = batch.changes.map { applyPush(shop, it) }
            val since = batch.cursor?.toLongOrNull() ?: if (batch.cursor == null) 0L
                else error("Invalid stored VPS cursor")
            require(since >= 0L)
            val remote = shop.rows.entries
                .filter { it.value.serverStamp > since }
                .sortedBy { it.value.serverStamp }
                .map { (key, row) ->
                    SyncChange(
                        operationId = "${key.first}:${key.second}:${row.serverStamp}",
                        entityType = key.first,
                        entityId = key.second,
                        operation = OperationType.UPDATE,
                        revision = 0L,
                        payload = row.payloadJson,
                        occurredAt = row.serverStamp,
                    )
                }
            return SyncResponse(receipts, remote, shop.clock.toString())
        }

        private fun applyPush(shop: Shop, change: SyncChange): SyncReceipt {
            fun accepted() = SyncReceipt(change.operationId, SyncOutcome.ACCEPTED)
            fun rejected() = SyncReceipt(change.operationId, SyncOutcome.REJECTED)
            val table = change.entityType
            val uuid = change.entityId
            if (table !in RoomSyncQueueStore.SUPPORTED_TABLES || uuid.isBlank()) return rejected()
            val key = table to uuid
            if (change.operation == OperationType.DELETE) {
                if (table !in SOFT_DELETE_TABLES) return rejected()
                val existing = shop.rows[key]?.takeIf { !it.deleted } ?: return rejected()
                val tombstone = JSONObject(existing.payloadJson)
                    .put("deleted_at", "server-stamp-${shop.clock + 1}").toString()
                shop.clock += 1
                shop.rows[key] = existing.copy(
                    payloadJson = tombstone, serverStamp = shop.clock, deleted = true
                )
                return accepted()
            }
            if (change.operation == OperationType.SYNC) return rejected()
            val payload = change.payload ?: return rejected()
            val json = try { JSONObject(payload) } catch (failure: Exception) { return rejected() }
            val writeStamp = shop.clock + 1
            // The handler uses the row's updated_at when present and stamps
            // NOW() otherwise; the double's NOW() analog is this write's stamp.
            val effectiveUpdatedAt =
                if (json.has("updated_at")) json.optLong("updated_at") else writeStamp
            val existing = shop.rows[key]?.takeIf { !it.deleted }
            if (table in INSERT_ONLY_TABLES && existing != null) {
                return if (businessFieldsEqual(existing.payloadJson, payload)) {
                    accepted()
                } else {
                    rejected()
                }
            }
            if (existing != null && effectiveUpdatedAt < existing.lwwUpdatedAt) return rejected()
            shop.clock = writeStamp
            shop.rows[key] = StoredRow(payload, effectiveUpdatedAt, writeStamp, deleted = false)
            return accepted()
        }

        /** Handler replay comparison: business fields only (uuid/updated_at excluded). */
        private fun businessFieldsEqual(first: String, second: String): Boolean {
            fun fields(payload: String): Map<String, String> {
                val json = JSONObject(payload)
                val result = sortedMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "uuid" || key == "updated_at") continue
                    result[key] = json.get(key).toString()
                }
                return result
            }
            return fields(first) == fields(second)
        }

        companion object {
            // The handler's immutable set (server/index.js): replays never update.
            private val INSERT_ONLY_TABLES = setOf(
                "account_transactions", "cash_movements", "stock_movements", "party_ledger"
            )
            // Subset of the handler's softDelete tables verified against
            // server/index.js; the tests exercise products (allowed) and
            // accounts (not soft-deletable) only.
            private val SOFT_DELETE_TABLES = setOf(
                "products", "services", "customers", "vehicles", "vendors", "expenses"
            )
        }
    }

    private fun seedParents(database: AppDatabase, identity: SyncIdentity) {
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

    private fun enqueue(
        database: AppDatabase,
        identity: SyncIdentity,
        operationId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String,
    ) {
        runBlocking {
            val now = System.currentTimeMillis()
            database.syncQueueDao().enqueue(SyncQueueEntity(
                id = operationId,
                tenantId = identity.tenantId,
                businessId = identity.businessId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadJson = payloadJson,
                revision = 1,
                status = "PENDING",
                attemptCount = 0,
                createdAt = now,
                updatedAt = now,
                deviceId = identity.deviceId,
            ))
        }
    }

    /**
     * The double is the oracle for the two-device tests, so its handler
     * semantics are verified directly at the seam first: exact operation-ID
     * receipts, LWW stale rejection, insert-only replay rules, delete rules.
     */
    @Test fun serverDoubleMirrorsProductionHandlerSemantics() = runBlocking {
        val server = InMemoryVpsServer()
        fun push(identity: SyncIdentity, change: SyncChange, cursor: String? = null) =
            runBlocking { server.exchange(SyncBatch(identity, listOf(change), cursor)) }
        fun pull(identity: SyncIdentity, cursor: String? = null) =
            runBlocking { server.exchange(SyncBatch(identity, emptyList(), cursor)) }

        // Editable insert, then a stale update is rejected and changes nothing.
        val created = push(identityA, SyncChange(
            "op-1", "products", "p-1", OperationType.CREATE, 1,
            """{"uuid":"p-1","name":"Newest","updated_at":2000}""", 2000L,
        ))
        assertEquals(listOf(SyncReceipt("op-1", SyncOutcome.ACCEPTED)), created.receipts)
        val stale = push(identityA, SyncChange(
            "op-2", "products", "p-1", OperationType.UPDATE, 2,
            """{"uuid":"p-1","name":"Stale","updated_at":1000}""", 1000L,
        ))
        assertEquals(listOf(SyncReceipt("op-2", SyncOutcome.REJECTED)), stale.receipts)
        assertEquals("Newest", JSONObject(
            pull(identityB).remoteChanges.single { it.entityId == "p-1" }.payload
        ).getString("name"))
        // An equal-updated_at update applies (the handler updates on stored <= incoming).
        val equal = push(identityB, SyncChange(
            "op-3", "products", "p-1", OperationType.UPDATE, 2,
            """{"uuid":"p-1","name":"Equal wins","updated_at":2000}""", 2000L,
        ))
        assertEquals(SyncOutcome.ACCEPTED, equal.receipts.single().outcome)

        // Insert-only: identical replay is an idempotent accept that creates
        // no new pull visibility; a differing replay is rejected.
        val ledger = SyncChange(
            "op-4", "account_transactions", "l-1", OperationType.CREATE, 1,
            """{"uuid":"l-1","amount":100,"updated_at":1000}""", 1000L,
        )
        assertEquals(SyncOutcome.ACCEPTED, push(identityA, ledger).receipts.single().outcome)
        val cursorAfterLedger = pull(identityB).nextCursor
        val replay = push(identityA, ledger.copy(operationId = "op-5",
            payload = """{"uuid":"l-1","amount":100,"updated_at":1001}"""))
        assertEquals(SyncOutcome.ACCEPTED, replay.receipts.single().outcome)
        assertTrue(pull(identityB, cursorAfterLedger).remoteChanges
            .none { it.entityId == "l-1" })
        val conflicting = push(identityA, ledger.copy(operationId = "op-6",
            payload = """{"uuid":"l-1","amount":999,"updated_at":1002}"""))
        assertEquals(SyncOutcome.REJECTED, conflicting.receipts.single().outcome)
        assertEquals(100, JSONObject(
            pull(identityB).remoteChanges.single { it.entityId == "l-1" }.payload
        ).getInt("amount"))

        // Unknown table rejected; delete only for soft-delete tables and
        // only for rows that exist in the shop.
        val unknown = push(identityA, SyncChange(
            "op-7", "audit_log", "x-1", OperationType.CREATE, 1,
            """{"uuid":"x-1"}""", 1000L,
        ))
        assertEquals(SyncOutcome.REJECTED, unknown.receipts.single().outcome)
        val deleteProduct = push(identityA, SyncChange(
            "op-8", "products", "p-1", OperationType.DELETE, 3, null, 3000L,
        ))
        assertEquals(SyncOutcome.ACCEPTED, deleteProduct.receipts.single().outcome)
        val deleteAccount = push(identityA, SyncChange(
            "op-9", "accounts", "a-1", OperationType.DELETE, 1, null, 3000L,
        ))
        assertEquals(SyncOutcome.REJECTED, deleteAccount.receipts.single().outcome)
        val deleteMissing = push(identityA, SyncChange(
            "op-10", "products", "p-missing", OperationType.DELETE, 1, null, 3000L,
        ))
        assertEquals(SyncOutcome.REJECTED, deleteMissing.receipts.single().outcome)
    }

    /**
     * 12C-05 FIXED (Package 9): a device's own push round settles even
     * though the pull in the same exchange echoes the pushed change back.
     *
     * History: in Package 8 this test pinned the opposite behavior as a
     * defect. The chain was: (1) the VPS pull returns every shop row
     * written after the device cursor, with no origin-device exclusion,
     * and the push handler stamps updated_at at push time (Android
     * payloads carry none), so the pull inside the very exchange that
     * pushes a change echoes that change back; (2)
     * AuthenticatedVpsTransport maps the echo to a remote change; (3)
     * SyncEngine applies remote changes BEFORE acknowledging outgoing
     * ones (a deliberate ordering, pinned by Package 7); (4)
     * RoomSyncQueueStore refused any incoming change for an entity with a
     * PENDING/FAILED local operation — and the just-acked operation was
     * still PENDING at that moment, so the round threw, the operation
     * never settled, and the cursor never advanced.
     *
     * The fix is the smallest seam from the Package 8 list: the engine
     * hands the store the operation IDs THIS response acknowledged, and
     * the store's guard no longer counts those operations as
     * unsynchronized local edits. The persistence ordering did not move —
     * incoming state is still applied before outgoing rows are marked
     * accepted — so the ack-then-crash window pinned by Package 7 keeps
     * its exact semantics; only the guard's view of same-response-acked
     * operations changed.
     */
    @Test fun pushRoundSettlesWhenPullEchoesOwnChange() = runBlocking {
        val databaseA = openDatabase()
        val databaseB = openDatabase()
        try {
            seedParents(databaseA, identityA)
            seedParents(databaseB, identityB)
            enqueue(databaseA, identityA, "op-a-1", "products", "product-1", "CREATE",
                """{"uuid":"product-1","name":"From A","updated_at":1000}""")
            val server = InMemoryVpsServer()
            val queueA = RoomSyncQueueStore(databaseA)
            val engineA = SyncEngine(queueA, server)

            // Round 1: the push is accepted and the pull inside the same
            // exchange echoes the change back. Before the fix this round
            // threw "conflicts with unsynchronized local edit" on every
            // attempt; now it settles.
            val first = engineA.run(StorageMode.CLOUD_SYNC, identityA)
            assertEquals(1, first.uploaded)
            // The echo was applied, not dropped: downloads counts it.
            assertEquals(1, first.downloaded)
            assertEquals(0, first.retryable)
            val row = databaseA.syncQueueDao().findById(
                identityA.tenantId, identityA.businessId, "op-a-1")
            assertNotNull(row)
            assertEquals("SYNCED", row!!.status)
            assertEquals("1", queueA.cursor(identityA))
            // A's staged record holds the server's copy of A's own row.
            val stagedA = databaseA.vpsBusinessRecordDao().get(
                identityA.tenantId, identityA.businessId, "products", "product-1")
            assertNotNull(stagedA)
            assertEquals("From A", JSONObject(stagedA!!.payloadJson).getString("name"))

            // Round 2: nothing pending and nothing new on the server — a
            // clean no-op that leaves the settled state untouched.
            val second = engineA.run(StorageMode.CLOUD_SYNC, identityA)
            assertEquals(0, second.uploaded)
            assertEquals(0, second.downloaded)
            assertEquals("1", queueA.cursor(identityA))

            // Device B receives A's row through its own real engine round:
            // author and peer converge on the same server state, which is
            // the end-to-end two-device settlement 12C-05(a) asked for.
            val pulledByB = SyncEngine(RoomSyncQueueStore(databaseB), server)
                .run(StorageMode.CLOUD_SYNC, identityB)
            assertEquals(1, pulledByB.downloaded)
            val stagedB = databaseB.vpsBusinessRecordDao().get(
                identityB.tenantId, identityB.businessId, "products", "product-1")
            assertNotNull(stagedB)
            assertEquals("From A", JSONObject(stagedB!!.payloadJson).getString("name"))
        } finally { databaseA.close(); databaseB.close() }
    }

    /**
     * The guard's other side, through the real engine: an operation the
     * server did NOT acknowledge in this response earns no exemption. A's
     * stale edit is rejected by the server's last-writer-wins check while
     * the same exchange's pull delivers B's newer row for the same
     * entity — the incoming change must still be refused and the whole
     * batch must roll back, exactly as before the fix.
     */
    @Test fun rejectedPushStillConflictsWithPulledChangeForSameEntity() = runBlocking {
        val server = InMemoryVpsServer()
        val databaseA = openDatabase()
        try {
            seedParents(databaseA, identityA)
            // B's newer edit of the product is already settled on the
            // server (server seam; asserted ACCEPTED with the exact ID).
            val settledB = server.exchange(SyncBatch(identityB, listOf(SyncChange(
                "op-b-1", "products", "product-1", OperationType.CREATE, 1,
                """{"uuid":"product-1","name":"From B","updated_at":2000}""", 2000L,
            )), null))
            assertEquals(
                listOf(SyncReceipt("op-b-1", SyncOutcome.ACCEPTED)), settledB.receipts)

            enqueue(databaseA, identityA, "op-a-stale", "products", "product-1", "UPDATE",
                """{"uuid":"product-1","name":"Stale A edit","updated_at":1000}""")
            val queueA = RoomSyncQueueStore(databaseA)
            try {
                SyncEngine(queueA, server).run(StorageMode.CLOUD_SYNC, identityA)
                fail("An unacknowledged local edit must still block the conflicting remote change")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message
                    ?.contains("conflicts with unsynchronized local edit") == true)
            }
            // Rolled back: nothing staged, the cursor never moved, and A's
            // operation is untouched — PENDING with its original attempt
            // count, re-presented identically on the next round.
            val row = databaseA.syncQueueDao().findById(
                identityA.tenantId, identityA.businessId, "op-a-stale")
            assertNotNull(row)
            assertEquals("PENDING", row!!.status)
            assertEquals(0, row.attemptCount)
            assertNull(queueA.cursor(identityA))
            assertNull(databaseA.vpsBusinessRecordDao().get(
                identityA.tenantId, identityA.businessId, "products", "product-1"))
            // The server still holds exactly B's row.
            val probe = server.exchange(SyncBatch(identityB, emptyList(), null))
            val held = probe.remoteChanges.filter { it.entityId == "product-1" }
            assertEquals(1, held.size)
            assertEquals("From B", JSONObject(held.single().payload).getString("name"))
        } finally { databaseA.close() }
    }

    /**
     * The delete side of the same composition, end to end: A's own delete
     * round settles even though the pull echoes the server tombstone back
     * in the same exchange (as an UPDATE carrying deleted_at — the only
     * form the production transport ever delivers), the store stages that
     * echo in canonical tombstone form, and B's replica list hides the
     * record after its next round. Before Package 9 the echoed tombstone
     * both tripped the conflict guard (the DELETE operation was still
     * PENDING at apply time) and, had it applied, would have left the row
     * visible because the staged payload carried deleted_at instead of
     * the _deleted form list() filters on.
     */
    @Test fun ownDeleteEchoSettlesAndHidesRecordOnBothDevices() = runBlocking {
        val server = InMemoryVpsServer()
        val databaseA = openDatabase()
        val databaseB = openDatabase()
        try {
            seedParents(databaseA, identityA)
            seedParents(databaseB, identityB)
            val queueA = RoomSyncQueueStore(databaseA)
            val queueB = RoomSyncQueueStore(databaseB)
            val repositoryA = RoomVpsBusinessRepository(databaseA.vpsBusinessRecordDao(), databaseA)
            val repositoryB = RoomVpsBusinessRepository(databaseB.vpsBusinessRecordDao(), databaseB)
            val scopeA = OperationalScope("tenant-a", "business-a", "device-a")
            val scopeB = OperationalScope("tenant-a", "business-a", "device-b")

            // A's create settles through its own engine round; B pulls it
            // and sees the live record in its replica list.
            enqueue(databaseA, identityA, "op-a-1", "products", "product-1", "CREATE",
                """{"uuid":"product-1","name":"From A","updated_at":1000}""")
            SyncEngine(queueA, server).run(StorageMode.CLOUD_SYNC, identityA)
            SyncEngine(queueB, server).run(StorageMode.CLOUD_SYNC, identityB)
            assertEquals(listOf("product-1"),
                repositoryB.list(scopeB, "products").map { it.entityId })

            // A deletes through the production repository: the staged
            // record becomes a _deleted tombstone and a DELETE operation
            // is queued.
            repositoryA.delete(scopeA, "products", "product-1")
            assertTrue(repositoryA.list(scopeA, "products").isEmpty())
            val deleteOperationId = queueA.pending(identityA, 20).single().operationId

            // A's delete round: the server accepts the delete and the
            // pull in the same exchange echoes the server tombstone back.
            // The round settles, and the record stays hidden on A.
            val deleteRound = SyncEngine(queueA, server)
                .run(StorageMode.CLOUD_SYNC, identityA)
            assertEquals(1, deleteRound.uploaded)
            assertEquals(1, deleteRound.downloaded)
            assertEquals("SYNCED", databaseA.syncQueueDao().findById(
                identityA.tenantId, identityA.businessId, deleteOperationId)?.status)
            assertEquals("2", queueA.cursor(identityA))
            assertTrue(repositoryA.list(scopeA, "products").isEmpty())

            // B's next round receives the same server tombstone and B's
            // replica list hides the record too.
            SyncEngine(queueB, server).run(StorageMode.CLOUD_SYNC, identityB)
            assertEquals("2", queueB.cursor(identityB))
            assertTrue(repositoryB.list(scopeB, "products").isEmpty())
        } finally { databaseA.close(); databaseB.close() }
    }

    /**
     * The propagation legs, proven with both devices' real engines and
     * stores: a change settled on the server reaches the other device,
     * and a later settled edit reaches back — with independent per-device
     * cursors and no cross-shop or cross-database contamination. The
     * changes here are settled at the server seam directly to isolate the
     * propagation legs; the sending device's own local settlement through
     * its engine round is proven separately by
     * [pushRoundSettlesWhenPullEchoesOwnChange]. Each direct settlement is
     * itself asserted ACCEPTED with the exact operation ID.
     */
    @Test fun settledChangesPropagateBetweenDevicesInBothDirections() = runBlocking {
        val server = InMemoryVpsServer()
        val databaseA = openDatabase()
        val databaseB = openDatabase()
        val databaseC = openDatabase()
        try {
            seedParents(databaseA, identityA)
            seedParents(databaseB, identityB)
            seedParents(databaseC, identityC)
            val queueA = RoomSyncQueueStore(databaseA)
            val queueB = RoomSyncQueueStore(databaseB)
            val queueC = RoomSyncQueueStore(databaseC)

            // A's change settles on the server (server seam).
            val settledA = server.exchange(SyncBatch(identityA, listOf(SyncChange(
                "op-a-1", "products", "product-1", OperationType.CREATE, 1,
                """{"uuid":"product-1","name":"From A","updated_at":1000}""", 1000L,
            )), null))
            assertEquals(
                listOf(SyncReceipt("op-a-1", SyncOutcome.ACCEPTED)), settledA.receipts)

            // B's real engine round pulls it into B's own database.
            val pulledByB = SyncEngine(queueB, server).run(StorageMode.CLOUD_SYNC, identityB)
            assertEquals(1, pulledByB.downloaded)
            val recordB = databaseB.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-1")
            assertNotNull(recordB)
            assertEquals("From A", JSONObject(recordB!!.payloadJson).getString("name"))
            assertEquals("1", queueB.cursor(identityB))
            // B's round touched only B's database: A has staged nothing yet.
            assertNull(databaseA.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-1"))

            // B's edit settles on the server (newer updated_at wins LWW).
            val settledB = server.exchange(SyncBatch(identityB, listOf(SyncChange(
                "op-b-1", "products", "product-1", OperationType.UPDATE, 2,
                """{"uuid":"product-1","name":"Edited by B","updated_at":2000}""", 2000L,
            )), queueB.cursor(identityB)))
            assertEquals(
                listOf(SyncReceipt("op-b-1", SyncOutcome.ACCEPTED)), settledB.receipts)

            // A's real engine round now receives B's edit. A's cursor is
            // independent of B's: A never pulled before, yet receives the
            // current server state.
            val pulledByA = SyncEngine(queueA, server).run(StorageMode.CLOUD_SYNC, identityA)
            assertEquals(1, pulledByA.downloaded)
            val recordA = databaseA.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-1")
            assertNotNull(recordA)
            assertEquals("Edited by B", JSONObject(recordA!!.payloadJson).getString("name"))
            assertEquals("2", queueA.cursor(identityA))
            // A's and C's rounds did not move B's cursor.
            assertEquals("1", queueB.cursor(identityB))

            // A device in a different business scope pulls nothing from
            // this shop, even from the same server.
            val pulledByC = SyncEngine(queueC, server).run(StorageMode.CLOUD_SYNC, identityC)
            assertEquals(0, pulledByC.downloaded)
            assertNull(databaseC.vpsBusinessRecordDao().get(
                "tenant-a", "business-b", "products", "product-1"))
            assertEquals("0", queueC.cursor(identityC))
        } finally {
            databaseA.close(); databaseB.close(); databaseC.close()
        }
    }
}
