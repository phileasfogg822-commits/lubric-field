package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.SyncChange
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import com.lubrcapp.lubrcapp.core.sync.SyncQueueStore

/**
 * Room-backed outgoing queue. Incoming entity application and durable cursor persistence
 * require a separate transactional inbox implementation; fail closed until available.
 */
class RoomSyncQueueStore(private val database: AppDatabase) : SyncQueueStore {
    override suspend fun pending(identity: SyncIdentity, limit: Int): List<SyncChange> =
        database.syncQueueDao().nextBatch(
            identity.tenantId,
            identity.businessId,
            listOf("PENDING", "FAILED"),
            System.currentTimeMillis(),
            limit.coerceIn(1, 200),
        ).filter { it.deviceId == identity.deviceId }.map { entity ->
            SyncChange(
                operationId = entity.id,
                entityType = entity.entityType,
                entityId = entity.entityId,
                operation = OperationType.valueOf(entity.operation.uppercase()),
                revision = entity.revision,
                payload = entity.payloadJson,
                occurredAt = entity.createdAt,
            )
        }

    override suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>) {
        for (id in operationIds) {
            val row = database.syncQueueDao().findById(identity.tenantId, identity.businessId, id)
                ?: continue
            if (row.deviceId != identity.deviceId) continue
            database.syncQueueDao().updateState(
                identity.tenantId, identity.businessId, id, "SYNCED",
                row.attemptCount, null, null, System.currentTimeMillis(),
            )
        }
    }

    override suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>) {
        for (id in operationIds) {
            val row = database.syncQueueDao().findById(identity.tenantId, identity.businessId, id)
                ?: continue
            if (row.deviceId != identity.deviceId) continue
            val attempts = row.attemptCount + 1
            val backoff = (1L shl attempts.coerceIn(0, 10)) * 1000L
            database.syncQueueDao().updateState(
                identity.tenantId, identity.businessId, id, "FAILED",
                attempts, "Retry required", System.currentTimeMillis() + backoff,
                System.currentTimeMillis(),
            )
        }
    }

    override suspend fun cursor(identity: SyncIdentity): String? = null

    override suspend fun applyRemoteAndSaveCursor(
        identity: SyncIdentity,
        changes: List<SyncChange>,
        nextCursor: String?,
    ) {
        require(changes.isEmpty() && nextCursor == null) {
            "Incoming sync is not yet implemented; refusing to acknowledge incomplete replication"
        }
    }
}
