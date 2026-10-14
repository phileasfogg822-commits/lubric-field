package com.lubrcapp.lubrcapp.core.store.repository

import androidx.room.withTransaction
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.sync.OperationType
import com.lubrcapp.lubrcapp.core.sync.SyncChange
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import com.lubrcapp.lubrcapp.core.sync.SyncQueueStore

/**
 * Room-backed outgoing queue and transactional remote staging inbox.
 * Incoming VPS rows are staged durably, not applied to domain entities until an
 * explicit per-entity mapping is installed. This prevents unsafe financial overwrites.
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

    private fun cursorKey(identity: SyncIdentity) = "vps.cursor.${identity.deviceId}"
    private fun inboxKey(identity: SyncIdentity, change: SyncChange) =
        "vps.inbox.${identity.deviceId}.${change.entityType}.${change.entityId}"

    override suspend fun cursor(identity: SyncIdentity): String? =
        database.settingsDao().get(identity.tenantId, identity.businessId, cursorKey(identity))?.value

    override suspend fun applyRemoteAndSaveCursor(
        identity: SyncIdentity,
        changes: List<SyncChange>,
        nextCursor: String?,
    ) {
        require(nextCursor != null && nextCursor.toLongOrNull()?.let { it >= 0 } == true) {
            "Remote cursor is required and must be a non-negative integer"
        }
        database.withTransaction {
            val settings = database.settingsDao()
            val old = settings.get(identity.tenantId, identity.businessId, cursorKey(identity))
            require((old?.value?.toLongOrNull() ?: 0L) <= nextCursor.toLong()) {
                "Refusing to roll back sync cursor"
            }
            val now = System.currentTimeMillis()
            for (change in changes) {
                require(change.entityType.matches(Regex("[a-z][a-z0-9_]*")))
                require(change.entityId.isNotBlank())
                require(change.payload != null) { "Incoming row payload required" }
                val key = inboxKey(identity, change)
                val previous = settings.get(identity.tenantId, identity.businessId, key)
                settings.upsert(
                    SettingEntity(
                        id = previous?.id ?: java.util.UUID.randomUUID().toString(),
                        tenantId = identity.tenantId,
                        businessId = identity.businessId,
                        settingKey = key,
                        value = requireNotNull(change.payload),
                        createdAt = previous?.createdAt ?: now,
                        updatedAt = now,
                        deviceId = identity.deviceId,
                        syncStatus = "LOCAL",
                    )
                )
            }
            settings.upsert(
                SettingEntity(
                    id = old?.id ?: java.util.UUID.randomUUID().toString(),
                    tenantId = identity.tenantId,
                    businessId = identity.businessId,
                    settingKey = cursorKey(identity),
                    value = nextCursor,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                    deviceId = identity.deviceId,
                    syncStatus = "LOCAL",
                )
            )
        }
    }
}
