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
 * Package 9 tombstone assessment: a server-side delete reaches the
 * Android store through the production transport as an UPDATE change
 * whose payload is the raw server row — the transport never emits
 * DELETE, and the row carries its tombstone as a non-null `deleted_at`
 * column rather than the `_deleted` flag the local delete path writes.
 *
 * Before this package, RoomSyncQueueStore staged that payload verbatim
 * and RoomVpsBusinessRepository.list (which filters on `_deleted` only)
 * kept showing the record: a pulled tombstone left the record VISIBLE.
 * The store now normalizes a non-null `deleted_at` on a non-DELETE
 * change into the canonical `_deleted` tombstone form at staging time,
 * so pulled tombstones behave exactly like local ones. These tests use
 * server-shaped payloads (raw rows, `deleted_at` null while live) and
 * the production repository read path as the oracle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PulledTombstoneVisibilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

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

    /** A pulled row exactly as the production transport delivers it. */
    private fun pulledRow(entityId: String, name: String, deletedAt: String?) = SyncChange(
        operationId = "products:$entityId:pull",
        entityType = "products",
        entityId = entityId,
        operation = OperationType.UPDATE,
        revision = 0L,
        payload = JSONObject()
            .put("uuid", entityId)
            .put("name", name)
            .put("deleted_at", deletedAt ?: JSONObject.NULL)
            .toString(),
        occurredAt = 100L,
    )

    @Test fun pulledTombstoneHidesRecordFromReplicaList() = runBlocking {
        val database = openDatabase()
        try {
            seedParents(database)
            val queue = RoomSyncQueueStore(database)
            val repository = RoomVpsBusinessRepository(database.vpsBusinessRecordDao(), database)

            // Two live rows arrive from the server; both are listed.
            queue.applyRemoteAndSaveCursor(identity, listOf(
                pulledRow("product-1", "Doomed", null),
                pulledRow("product-2", "Survivor", null),
            ), "1")
            assertEquals(
                listOf("product-1", "product-2"),
                repository.list(scope, "products").map { it.id }.sorted(),
            )

            // The server deletes product-1; the next pull delivers the
            // tombstone in its only production form.
            queue.applyRemoteAndSaveCursor(identity, listOf(
                pulledRow("product-1", "Doomed", "2026-10-22T01:00:00.000Z"),
            ), "2")

            // The replica list hides the deleted record and keeps the
            // survivor — the pulled tombstone now behaves like a local one.
            assertEquals(
                listOf("product-2"),
                repository.list(scope, "products").map { it.id },
            )
            // The row is staged, not dropped: it remains in the replica
            // store in canonical tombstone form, with the server's
            // deleted_at preserved alongside the _deleted flag.
            val staged = database.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-1")
            assertNotNull(staged)
            val stagedJson = JSONObject(staged!!.payloadJson)
            assertTrue(stagedJson.getBoolean("_deleted"))
            assertEquals("2026-10-22T01:00:00.000Z", stagedJson.getString("deleted_at"))
            assertEquals("2", queue.cursor(identity))
        } finally { database.close() }
    }

    @Test fun liveRowWithNullDeletedAtStaysVisible() = runBlocking {
        val database = openDatabase()
        try {
            seedParents(database)
            val queue = RoomSyncQueueStore(database)
            val repository = RoomVpsBusinessRepository(database.vpsBusinessRecordDao(), database)

            // Every live server row carries the deleted_at column as
            // JSON null; that must not be mistaken for a tombstone.
            queue.applyRemoteAndSaveCursor(identity, listOf(
                pulledRow("product-1", "Live", null),
            ), "1")
            assertEquals(
                listOf("product-1"),
                repository.list(scope, "products").map { it.id },
            )
            val staged = database.vpsBusinessRecordDao().get(
                "tenant-a", "business-a", "products", "product-1")
            assertNotNull(staged)
            assertTrue(!JSONObject(staged!!.payloadJson).optBoolean("_deleted", false))
        } finally { database.close() }
    }
}
