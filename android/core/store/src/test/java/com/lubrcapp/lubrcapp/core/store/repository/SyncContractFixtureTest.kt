package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.SyncChange
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 12C-10: the shared sync contract fixture is the single source every client
 * is tested against (VPS push handler, desktop agent, this Android store).
 * The canonical file lives at the repository root in `sync-contract/`; the
 * copy on this test classpath must stay byte-identical to it, and the
 * fixture's canonical pull exchanges are driven through the production
 * [RoomSyncQueueStore] so the contract is behavioral, not just declarative.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncContractFixtureTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")

    private fun fixtureText(): String =
        javaClass.getResourceAsStream("/sync-contract.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("sync-contract.json missing from test resources")

    private fun fixture() = JSONObject(fixtureText())

    private fun stringSet(array: JSONArray): Set<String> =
        (0 until array.length()).map { array.getString(it) }.toSet()

    @Test fun androidCopyMatchesCanonicalFixture() {
        var dir: File? = File(System.getProperty("user.dir"))
        var canonical: File? = null
        while (dir != null && canonical == null) {
            val candidate = File(dir, "sync-contract/sync-contract.json")
            if (candidate.isFile) canonical = candidate else dir = dir.parentFile
        }
        assertNotNull(
            "canonical sync-contract/sync-contract.json not found above " +
                System.getProperty("user.dir"),
            canonical,
        )
        assertEquals(canonical!!.readText(), fixtureText())
    }

    @Test fun fixtureTableSetsMatchProductionConstants() {
        val fixture = fixture()
        assertEquals(
            RoomSyncQueueStore.SUPPORTED_TABLES,
            stringSet(fixture.getJSONArray("supportedTables")),
        )
        val insertOnly = stringSet(fixture.getJSONArray("insertOnlyTables"))
        assertEquals(
            setOf("account_transactions", "cash_movements", "party_ledger", "stock_movements"),
            insertOnly,
        )
        assertTrue(RoomSyncQueueStore.SUPPORTED_TABLES.containsAll(insertOnly))
    }

    private fun seed(database: AppDatabase) {
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

    private fun changesOf(response: JSONObject): List<SyncChange> {
        val changes = response.getJSONObject("changes")
        val result = mutableListOf<SyncChange>()
        val tables = changes.keys()
        var sequence = 0
        while (tables.hasNext()) {
            val table = tables.next()
            val rows = changes.getJSONArray(table)
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                sequence += 1
                result += SyncChange(
                    operationId = "op-contract-$sequence",
                    entityType = table,
                    entityId = row.getString("uuid"),
                    operation = OperationType.CREATE,
                    revision = 1,
                    payload = row.toString(),
                    occurredAt = 100L + sequence,
                )
            }
        }
        return result
    }

    @Test fun fixturePullAppliesAndInsertOnlyReplayPreservesPosting() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            seed(db)
            val queue = RoomSyncQueueStore(db)
            val pull = fixture().getJSONObject("pullExchange")
            val expect = pull.getJSONObject("expect")
            val response = pull.getJSONObject("response")
            val ledgerRow = response.getJSONObject("changes")
                .getJSONArray("account_transactions").getJSONObject(0)
            val ledgerUuid = ledgerRow.getString("uuid")

            queue.applyRemoteAndSaveCursor(
                identity, changesOf(response), response.getLong("cursor").toString(),
            )
            val stored = db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "account_transactions", ledgerUuid,
            )
            assertNotNull(stored)
            assertEquals(
                expect.getInt("storedLedgerAmount"),
                JSONObject(stored!!.payloadJson).getInt("amount"),
            )
            assertNotNull(db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "products",
                response.getJSONObject("changes").getJSONArray("products")
                    .getJSONObject(0).getString("uuid"),
            ))
            assertEquals(expect.getString("cursorAfter"), queue.cursor(identity))

            // The replay carries the same ledger UUID with a different amount:
            // insert-only parity keeps the stored posting, and the cursor
            // still advances.
            val replay = pull.getJSONObject("replayResponse")
            queue.applyRemoteAndSaveCursor(
                identity, changesOf(replay), replay.getLong("cursor").toString(),
            )
            val afterReplay = db.vpsBusinessRecordDao().get(
                identity.tenantId, identity.businessId, "account_transactions", ledgerUuid,
            )
            assertEquals(
                expect.getInt("storedLedgerAmount"),
                JSONObject(afterReplay!!.payloadJson).getInt("amount"),
            )
            assertEquals(expect.getString("cursorAfterReplay"), queue.cursor(identity))
        } finally { db.close() }
    }

    @Test fun fixtureRegressingCursorIsRejectedAndStoredCursorPreserved() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            seed(db)
            val queue = RoomSyncQueueStore(db)
            val pull = fixture().getJSONObject("pullExchange")
            val response = pull.getJSONObject("response")
            runBlocking {
                queue.applyRemoteAndSaveCursor(
                    identity, changesOf(response), response.getLong("cursor").toString(),
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    queue.applyRemoteAndSaveCursor(
                        identity, emptyList(), pull.getInt("regressedCursor").toString(),
                    )
                }
            }
            assertEquals(
                pull.getJSONObject("expect").getString("cursorAfter"),
                runBlocking { queue.cursor(identity) },
            )
        } finally { db.close() }
    }
}
