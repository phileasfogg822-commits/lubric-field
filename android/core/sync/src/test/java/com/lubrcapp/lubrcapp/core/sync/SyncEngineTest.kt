package com.lubrcapp.lubrcapp.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncEngineTest {
    private val identity = SyncIdentity("tenant", "business", "device")
    private val change = SyncChange("one", "business", "entity", OperationType.UPDATE, 1, "{}", 100)

    private class Store : SyncQueueStore {
        var accepted = emptySet<String>()
        var retried = emptySet<String>()
        var applied = false
        var appliedAcknowledged: Set<String>? = null
        override suspend fun pending(identity: SyncIdentity, limit: Int) = listOf(
            SyncChange("one", "business", "entity", OperationType.UPDATE, 1, "{}", 100)
        )
        override suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>) {
            check(applied)
            accepted = operationIds
        }
        override suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>) {
            retried = operationIds
        }
        override suspend fun cursor(identity: SyncIdentity): String? = null
        override suspend fun applyRemoteAndSaveCursor(
            identity: SyncIdentity,
            changes: List<SyncChange>,
            nextCursor: String?,
            acknowledgedOperationIds: Set<String>,
        ) {
            applied = true
            appliedAcknowledged = acknowledgedOperationIds
        }
    }


    @Test fun networkTimeoutKeepsOutgoingChangeRetryable() = runBlocking {
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch): SyncResponse {
                throw java.net.SocketTimeoutException("simulated timeout")
            }
        }
        try {
            SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
            org.junit.Assert.fail("Expected timeout")
        } catch (_: java.net.SocketTimeoutException) {
            assertEquals(setOf("one"), store.retried)
            assertEquals(emptySet<String>(), store.accepted)
            assertEquals(false, store.applied)
        }
    }

    @Test fun duplicateReceiptAcknowledgesOnlyAfterRemotePersistence() = runBlocking {
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(listOf(SyncReceipt("one", SyncOutcome.DUPLICATE)), emptyList(), "1")
        }
        assertEquals(1, SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity).uploaded)
        assertEquals(setOf("one"), store.accepted)
    }

    @Test fun unknownReceiptCannotAcknowledgeLocalChanges() = runBlocking {
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(listOf(SyncReceipt("other", SyncOutcome.ACCEPTED)), emptyList(), "1")
        }
        try {
            SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
            org.junit.Assert.fail("Unknown receipt should fail")
        } catch (_: IllegalArgumentException) {
            assertEquals(emptySet<String>(), store.accepted)
            assertEquals(false, store.applied)
        }
    }

    @Test fun localOnlyNeverCallsNetwork() = runBlocking {
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch): SyncResponse {
                error("Network must not be used")
            }
        }
        assertEquals(0, SyncEngine(store, transport).run(StorageMode.LOCAL_ONLY, identity).uploaded)
    }

    @Test fun acceptedChangesAreAcknowledgedOnlyAfterRemotePersistence() = runBlocking {
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(listOf(SyncReceipt("one", SyncOutcome.ACCEPTED)), emptyList(), "cursor")
        }
        assertEquals(1, SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity).uploaded)
        assertEquals(setOf("one"), store.accepted)
    }

    @Test fun rejectedChangesRemainRetryable() = runBlocking {
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(listOf(SyncReceipt("one", SyncOutcome.CONFLICT)), emptyList(), null)
        }
        assertEquals(1, SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity).retryable)
        assertEquals(setOf("one"), store.retried)
    }
    @Test fun persistenceFailureNeverAcknowledgesOutgoingChange() = runBlocking {
        val store = object : SyncQueueStore {
            var acknowledged = false
            override suspend fun pending(identity: SyncIdentity, limit: Int) = listOf(change)
            override suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>) {
                acknowledged = true
            }
            override suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>) = Unit
            override suspend fun cursor(identity: SyncIdentity): String? = null
            override suspend fun applyRemoteAndSaveCursor(
                identity: SyncIdentity,
                changes: List<SyncChange>,
                nextCursor: String?,
                acknowledgedOperationIds: Set<String>,
            ) {
                throw IllegalStateException("simulated database rollback")
            }
        }
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(listOf(SyncReceipt("one", SyncOutcome.ACCEPTED)), emptyList(), "2")
        }
        try {
            SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
            org.junit.Assert.fail("Persistence failure must propagate")
        } catch (_: IllegalStateException) {
            assertEquals(false, store.acknowledged)
        }
    }

    @Test fun partialReceiptsLeaveUnacknowledgedChangesRetryable() = runBlocking {
        val store = object : SyncQueueStore {
            var accepted = emptySet<String>()
            var retry = emptySet<String>()
            override suspend fun pending(identity: SyncIdentity, limit: Int) =
                listOf(change, change.copy(operationId = "two"))
            override suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>) {
                accepted = operationIds
            }
            override suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>) {
                retry = operationIds
            }
            override suspend fun cursor(identity: SyncIdentity): String? = null
            override suspend fun applyRemoteAndSaveCursor(
                identity: SyncIdentity,
                changes: List<SyncChange>,
                nextCursor: String?,
                acknowledgedOperationIds: Set<String>,
            ) = Unit
        }
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(listOf(SyncReceipt("one", SyncOutcome.ACCEPTED)), emptyList(), "3")
        }
        val result = SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
        assertEquals(1, result.uploaded)
        assertEquals(1, result.retryable)
        assertEquals(setOf("one"), store.accepted)
        assertEquals(setOf("two"), store.retry)
    }

    @Test fun emptyReceiptsLeaveAllChangesRetryable() = runBlocking {
        val store = object : SyncQueueStore {
            var accepted = emptySet<String>()
            var retry = emptySet<String>()
            override suspend fun pending(identity: SyncIdentity, limit: Int) = listOf(change)
            override suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>) {
                accepted = operationIds
            }
            override suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>) {
                retry = operationIds
            }
            override suspend fun cursor(identity: SyncIdentity): String? = null
            override suspend fun applyRemoteAndSaveCursor(
                identity: SyncIdentity,
                changes: List<SyncChange>,
                nextCursor: String?,
                acknowledgedOperationIds: Set<String>,
            ) = Unit
        }
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(emptyList(), emptyList(), "4")
        }
        val result = SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
        assertEquals(0, result.uploaded)
        assertEquals(1, result.retryable)
        assertEquals(emptySet<String>(), store.accepted)
        assertEquals(setOf("one"), store.retry)
    }

    @Test fun duplicateReceiptsAcknowledgeOperationOnlyOnce() = runBlocking {
        val store = object : SyncQueueStore {
            var accepted = emptySet<String>()
            var retry = emptySet<String>()
            override suspend fun pending(identity: SyncIdentity, limit: Int) = listOf(change)
            override suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>) {
                accepted = operationIds
            }
            override suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>) {
                retry = operationIds
            }
            override suspend fun cursor(identity: SyncIdentity): String? = null
            override suspend fun applyRemoteAndSaveCursor(
                identity: SyncIdentity,
                changes: List<SyncChange>,
                nextCursor: String?,
                acknowledgedOperationIds: Set<String>,
            ) = Unit
        }
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(
                    listOf(
                        SyncReceipt("one", SyncOutcome.ACCEPTED),
                        SyncReceipt("one", SyncOutcome.DUPLICATE),
                    ),
                    emptyList(),
                    "5",
                )
        }
        val result = SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
        assertEquals(1, result.uploaded)
        assertEquals(0, result.retryable)
        assertEquals(setOf("one"), store.accepted)
        assertEquals(emptySet<String>(), store.retry)
    }

    @Test fun acceptedOperationIdsArePassedToRemoteApply() = runBlocking {
        // 12C-05 fix: the store's unsynchronized-edit guard must know which
        // operations THIS response acknowledged, so the pull echo of a
        // just-pushed change is not refused. The engine passes exactly the
        // accepted set — still before markAccepted runs (the Store fake
        // asserts that ordering itself via check(applied)).
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(
                    listOf(SyncReceipt("one", SyncOutcome.ACCEPTED)),
                    listOf(change.copy(operationId = "remote-echo")),
                    "9",
                )
        }
        val result = SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
        assertEquals(1, result.uploaded)
        assertEquals(1, result.downloaded)
        assertEquals(setOf("one"), store.appliedAcknowledged)
        assertEquals(setOf("one"), store.accepted)
    }

    @Test fun unacknowledgedOperationsAreNotPassedToRemoteApplyAsExempt() = runBlocking {
        // The complement: an operation the server did NOT acknowledge in
        // this response gets no exemption — the guard must still see it as
        // an unsynchronized local edit.
        val store = Store()
        val transport = object : SyncTransport {
            override suspend fun exchange(batch: SyncBatch) =
                SyncResponse(
                    listOf(SyncReceipt("one", SyncOutcome.CONFLICT)),
                    emptyList(),
                    "9",
                )
        }
        val result = SyncEngine(store, transport).run(StorageMode.CLOUD_SYNC, identity)
        assertEquals(1, result.retryable)
        assertEquals(emptySet<String>(), store.appliedAcknowledged)
        assertEquals(setOf("one"), store.retried)
    }

}
