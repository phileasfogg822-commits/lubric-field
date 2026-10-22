package com.lubrcapp.lubrcapp.core.store.repository

import androidx.room.withTransaction
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.VpsBusinessRecordEntity
import org.json.JSONObject
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
    companion object {
        private val DELETABLE_TABLES = setOf("products", "services", "customers", "vehicles", "vendors", "reminders", "price_lists", "price_list_items", "party_price_lists", "party_prices", "customer_groups", "units", "unit_conversions", "warehouses", "warehouse_locations")

        /**
         * Ledger tables whose rows are immutable postings. Mirrors the VPS push
         * handler's immutable set and the desktop agent's INSERT_ONLY_SYNC_TABLES:
         * an incoming replay never overwrites an existing local posting.
         */
        private val INSERT_ONLY_TABLES = setOf("account_transactions", "cash_movements", "stock_movements", "party_ledger")

        /** Every business table the VPS sync protocol may carry; shared with device pairing. */
        val SUPPORTED_TABLES = setOf("products", "services", "customers", "vehicles", "vendors", "orders", "order_items", "expenses", "accounts", "account_transactions", "purchase_orders", "vendor_payables", "reminders", "inspections", "cash_movements", "reservations", "party_ledger", "commitments", "stock_movements", "price_lists", "price_list_items", "party_price_lists", "party_prices", "customer_groups", "units", "unit_conversions", "warehouses", "warehouse_locations")
    }

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
        acknowledgedOperationIds: Set<String>,
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
                require(change.entityType in SUPPORTED_TABLES) { "Unsupported VPS business table" }
                require(change.entityId.isNotBlank())
                require(change.payload != null) { "Incoming row payload required" }
                val json = JSONObject(requireNotNull(change.payload))
                require(json.getString("uuid") == change.entityId) { "VPS row UUID mismatch" }
                if (change.operation == OperationType.DELETE) {
                    require(change.entityType in DELETABLE_TABLES && json.optBoolean("_deleted", false)) {
                        "Delete requires an approved nonfinancial tombstone mapping"
                    }
                } else {
                    require(!json.optBoolean("_deleted", false)) { "Tombstone must use DELETE operation" }
                }
                // The VPS pull returns raw rows and the production transport
                // maps every one of them to UPDATE — a server tombstone
                // therefore arrives as an UPDATE whose payload carries a
                // non-null deleted_at, never as a DELETE change. Stage it in
                // the canonical tombstone form (the same _deleted form local
                // deletes write) so replica reads filter it exactly like a
                // local tombstone; without this the row stays visible in
                // RoomVpsBusinessRepository.list after a remote delete.
                val serverTombstone = change.operation != OperationType.DELETE &&
                    json.has("deleted_at") && !json.isNull("deleted_at")
                if (serverTombstone) json.put("_deleted", true)
                // Conflict guard: a pending local edit for the same entity
                // refuses the incoming change — EXCEPT an operation the
                // server acknowledged in this same response. That operation
                // is still PENDING here (the engine persists incoming state
                // before marking outgoing rows accepted), but it is no
                // longer unsynchronized: the incoming change is its own
                // pull echo or a newer server state, and applying it is
                // what lets the device's own push round settle (12C-05).
                val pendingLocal = database.syncQueueDao().nextBatch(
                    identity.tenantId, identity.businessId,
                    listOf("PENDING", "FAILED"), Long.MAX_VALUE, 200
                ).any {
                    it.entityType == change.entityType && it.entityId == change.entityId &&
                        it.id !in acknowledgedOperationIds
                }
                require(!pendingLocal) { "Remote change conflicts with unsynchronized local edit" }
                // Insert-only parity: a replayed ledger posting that is already
                // stored locally is an idempotent no-op, never an overwrite —
                // the same rule the VPS handler and the desktop agent apply.
                if (change.entityType in INSERT_ONLY_TABLES &&
                    database.vpsBusinessRecordDao().get(
                        identity.tenantId, identity.businessId, change.entityType, change.entityId
                    ) != null
                ) {
                    continue
                }
                database.vpsBusinessRecordDao().upsert(
                    VpsBusinessRecordEntity(
                        identity.tenantId, identity.businessId, change.entityType,
                        change.entityId, json.toString(), now
                    )
                )
                val key = inboxKey(identity, change)
                val previous = settings.get(identity.tenantId, identity.businessId, key)
                settings.upsert(
                    SettingEntity(
                        id = previous?.id ?: java.util.UUID.randomUUID().toString(),
                        tenantId = identity.tenantId,
                        businessId = identity.businessId,
                        settingKey = key,
                        // Stage the normalized form for server tombstones so
                        // the inbox agrees with the staged record above.
                        value = if (serverTombstone) json.toString()
                            else requireNotNull(change.payload),
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
