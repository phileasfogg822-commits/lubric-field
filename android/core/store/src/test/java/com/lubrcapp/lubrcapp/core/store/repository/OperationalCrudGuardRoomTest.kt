package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Broader operational CRUD contract for the Room repository (12B-10):
 * the observe Flow every screen consumes, the save validation guards,
 * the financial delete contract, and delete scope isolation. Create/edit
 * persistence across restart is covered by
 * [OperationalBusinessRoomIntegrationTest]; this class pins the guard and
 * isolation semantics that class does not exercise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OperationalCrudGuardRoomTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")

    private fun inMemoryDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries().build()

    @Test fun observeReflectsCreateEditAndDeleteWithinScopeOnly() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val other = OperationalScope("tenant-a", "business-b", "device-b")
            assertTrue(repo.observe(scope, "products").first().isEmpty())
            repo.save(scope, "products", "p-1", """{"uuid":"p-1","name":"Before"}""")
            val created = repo.observe(scope, "products").first()
            assertEquals(listOf("p-1"), created.map { it.id })
            val edited = JSONObject(created.single().json).put("name", "After").toString()
            repo.save(scope, "products", "p-1", edited)
            val afterEdit = repo.observe(scope, "products").first()
            assertEquals(1, afterEdit.size)
            assertEquals("After", JSONObject(afterEdit.single().json).getString("name"))
            repo.delete(scope, "products", "p-1")
            assertTrue(repo.observe(scope, "products").first().isEmpty())
            assertTrue(repo.observe(other, "products").first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun saveRejectsInvalidPayloadsAndScopesWithoutStagingAnything() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            assertSaveRejected(repo, scope, "products", "p-1",
                """{"uuid":"other-id","name":"Mismatch"}""", "Payload UUID mismatch")
            assertSaveRejected(repo, scope, "products", "p-1",
                """{"uuid":"p-1","_deleted":true}""", "Cannot save a tombstone as a live record")
            assertSaveRejected(repo, scope, "not_a_table", "p-1",
                """{"uuid":"p-1"}""", "Unsupported operational entity type")
            assertSaveRejected(repo, OperationalScope("", "business-a", "device-a"), "products", "p-1",
                """{"uuid":"p-1"}""", "Tenant and business are required")
            assertSaveRejected(repo, scope, "products", "p-1",
                """{"uuid":"p-1"}""", null, revision = -1)
            assertTrue(repo.list(scope, "products").isEmpty())
            assertTrue(RoomSyncQueueStore(db).pending(identity, 20).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun financialTablesRejectDeleteAndKeepRecord() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val financial = listOf("orders", "purchase_orders", "accounts", "stock_movements")
            financial.forEachIndexed { index, table ->
                val id = "fin-$index"
                repo.save(scope, table, id, JSONObject()
                    .put("uuid", id).put("description", "Original").toString())
                try {
                    repo.delete(scope, table, id)
                    fail("Delete must be rejected for " + table)
                } catch (expected: IllegalArgumentException) {
                    assertTrue(expected.message!!.contains("reversal"))
                }
                assertEquals(1, repo.list(scope, table).size)
            }
            val queued = RoomSyncQueueStore(db).pending(identity, 50)
            assertEquals(4, queued.size)
            assertTrue(queued.all { it.operation == OperationType.CREATE })
        } finally {
            db.close()
        }
    }

    @Test fun deleteRequiresExistingRecordInScope() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val otherBusiness = OperationalScope("tenant-a", "business-b", "device-b")
            repo.save(scope, "products", "p-1", """{"uuid":"p-1","name":"Mine"}""")
            try {
                repo.delete(scope, "products", "missing")
                fail("Delete of a missing record must fail")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("Record does not exist in scope"))
            }
            try {
                repo.delete(otherBusiness, "products", "p-1")
                fail("Delete from another scope must fail")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("Record does not exist in scope"))
            }
            assertEquals(1, repo.list(scope, "products").size)
            val queued = RoomSyncQueueStore(db).pending(identity, 20)
            assertEquals(listOf(OperationType.CREATE), queued.map { it.operation })
        } finally {
            db.close()
        }
    }

    @Test fun deleteInOneScopeLeavesOtherScopeRecordAndQueueUntouched() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            val scopeB = OperationalScope("tenant-a", "business-b", "device-b")
            val identityB = SyncIdentity("tenant-a", "business-b", "device-b")
            repo.save(scope, "products", "shared", """{"uuid":"shared","name":"Scope A"}""")
            repo.save(scopeB, "products", "shared", """{"uuid":"shared","name":"Scope B"}""")
            repo.delete(scope, "products", "shared")
            assertTrue(repo.list(scope, "products").isEmpty())
            val surviving = repo.list(scopeB, "products")
            assertEquals(1, surviving.size)
            assertEquals("Scope B", JSONObject(surviving.single().json).getString("name"))
            val queueA = RoomSyncQueueStore(db).pending(identity, 20)
            assertEquals(listOf(OperationType.CREATE, OperationType.DELETE), queueA.map { it.operation })
            val queueB = RoomSyncQueueStore(db).pending(identityB, 20)
            assertEquals(listOf(OperationType.CREATE), queueB.map { it.operation })
        } finally {
            db.close()
        }
    }

    @Test fun financialCorrectionIsACompensatingEntryNotADelete() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.save(scope, "accounts", "acct-1", JSONObject()
                .put("uuid", "acct-1").put("description", "Cash sale").put("amount", 500).toString())
            // The correction is a second, compensating entry; history stays append-only.
            repo.save(scope, "accounts", "acct-2", JSONObject()
                .put("uuid", "acct-2").put("description", "Reversal of acct-1")
                .put("amount", -500).put("reverses", "acct-1").toString())
            assertEquals(2, repo.list(scope, "accounts").size)
            val queued = RoomSyncQueueStore(db).pending(identity, 20)
            assertEquals(2, queued.size)
            assertTrue(queued.all { it.operation == OperationType.CREATE })
        } finally {
            db.close()
        }
    }

    @Test fun saveAfterDeleteRestoresRecordAndQueuesUpdate() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.save(scope, "products", "p-1", """{"uuid":"p-1","name":"Before"}""")
            repo.delete(scope, "products", "p-1")
            assertTrue(repo.list(scope, "products").isEmpty())
            repo.save(scope, "products", "p-1", """{"uuid":"p-1","name":"Restored"}""")
            val restored = repo.list(scope, "products")
            assertEquals(1, restored.size)
            assertEquals("Restored", JSONObject(restored.single().json).getString("name"))
            val queued = RoomSyncQueueStore(db).pending(identity, 20)
            assertEquals(
                listOf(OperationType.CREATE, OperationType.DELETE, OperationType.UPDATE),
                queued.map { it.operation },
            )
        } finally {
            db.close()
        }
    }

    // 2026-10-27 device-crash regression: typed codecs (ItemJson,
    // PartyJson, …) never write a uuid key, so saveOffline rejected
    // every typed save; and the Phase 12 table list never grew with
    // the record types Phases 15–20 added, so Items/Inventory/
    // Accounts crashed on open (validate throws during composition).
    @Test fun saveWithoutUuidInjectsRecordIdInsteadOfRejecting() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.save(scope, "categories", "c-1", """{"name":"Oil"}""")
            val stored = repo.list(scope, "categories")
            assertEquals(1, stored.size)
            assertEquals("c-1", JSONObject(stored.single().json).getString("uuid"))
            assertEquals("Oil", JSONObject(stored.single().json).getString("name"))
        } finally {
            db.close()
        }
    }

    @Test fun laterPhaseTablesAreObservable() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            listOf(
                "categories", "brands", "branches", "batches",
                "stock_levels", "stock_transfers", "serial_numbers",
                "journal_entries", "timeline_entries", "numbering_sequences",
            ).forEach { table ->
                assertTrue(repo.observe(scope, table).first().isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test fun masterDataTablesDeleteByTombstone() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = RoomVpsBusinessRepository(db.vpsBusinessRecordDao(), db)
            repo.save(scope, "categories", "c-1", """{"name":"Oil"}""")
            repo.delete(scope, "categories", "c-1")
            assertTrue(repo.observe(scope, "categories").first().isEmpty())
        } finally {
            db.close()
        }
    }

    private suspend fun assertSaveRejected(
        repo: RoomVpsBusinessRepository,
        scope: OperationalScope,
        table: String,
        id: String,
        json: String,
        messagePart: String?,
        revision: Long = 0,
    ) {
        try {
            repo.save(scope, table, id, json, revision)
            fail("Save must be rejected: " + (messagePart ?: "invalid input"))
        } catch (expected: IllegalArgumentException) {
            if (messagePart != null) {
                assertTrue(expected.message!!.contains(messagePart))
            }
        }
    }
}
