package com.lubrcapp.lubrcapp.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncContractTest {
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")

    @Test
    fun localOnlyNeverTransfersToCloud() {
        assertFalse(SyncContract.canTransfer(StorageMode.LOCAL_ONLY, identity))
        assertTrue(SyncContract.canTransfer(StorageMode.CLOUD_SYNC, identity))
    }

    @Test
    fun incompleteTenantScopeCannotTransfer() {
        assertFalse(
            SyncContract.canTransfer(
                StorageMode.CLOUD_SYNC,
                identity.copy(businessId = ""),
            )
        )
    }

    @Test
    fun duplicateOperationsAreRejectedBeforeUpload() {
        val change = SyncChange("op-1", "BUSINESS", "business-a", OperationType.UPDATE, 1, null, 100)
        val batch = SyncBatch(identity, listOf(change, change))
        assertTrue(runCatching { SyncContract.validateBatch(batch) }.isFailure)
    }

    @Test
    fun validOfflineChangeCanBeQueuedForLaterSync() {
        val change = SyncChange("op-1", "BUSINESS", "business-a", OperationType.CREATE, 0, "{}", 100)
        val batch = SyncBatch(identity, listOf(change))
        SyncContract.validateBatch(batch)
        assertFalse(SyncContract.canTransfer(StorageMode.LOCAL_ONLY, batch.identity))
    }
}
