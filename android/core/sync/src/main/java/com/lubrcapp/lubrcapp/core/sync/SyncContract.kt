package com.lubrcapp.lubrcapp.core.sync

/**
 * Phase 12: platform-neutral protocol contract for Android, desktop and VPS.
 * Local writes never depend on a network request; remote replication is opt-in.
 */
enum class StorageMode { LOCAL_ONLY, CLOUD_SYNC }

data class SyncIdentity(
    val tenantId: String,
    val businessId: String,
    val deviceId: String,
)

data class SyncChange(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operation: OperationType,
    val revision: Long,
    val payload: String?,
    val occurredAt: Long,
)

data class SyncBatch(
    val identity: SyncIdentity,
    val changes: List<SyncChange>,
    val cursor: String? = null,
)

enum class SyncOutcome { ACCEPTED, DUPLICATE, CONFLICT, REJECTED }

data class SyncReceipt(
    val operationId: String,
    val outcome: SyncOutcome,
    val serverRevision: Long? = null,
)

data class SyncResponse(
    val receipts: List<SyncReceipt>,
    val remoteChanges: List<SyncChange>,
    val nextCursor: String?,
)

object SyncContract {
    const val VERSION = 1

    fun canTransfer(mode: StorageMode, identity: SyncIdentity): Boolean =
        mode == StorageMode.CLOUD_SYNC &&
            identity.tenantId.isNotBlank() &&
            identity.businessId.isNotBlank() &&
            identity.deviceId.isNotBlank()

    fun validateBatch(batch: SyncBatch) {
        require(batch.identity.tenantId.isNotBlank())
        require(batch.identity.businessId.isNotBlank())
        require(batch.identity.deviceId.isNotBlank())
        require(batch.changes.map { it.operationId }.distinct().size == batch.changes.size) {
            "Duplicate operation IDs in sync batch"
        }
        batch.changes.forEach {
            require(it.operationId.isNotBlank())
            require(it.entityType.isNotBlank())
            require(it.entityId.isNotBlank())
            require(it.revision >= 0)
        }
    }
}
