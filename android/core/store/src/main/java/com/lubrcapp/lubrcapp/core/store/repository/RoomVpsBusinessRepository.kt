package com.lubrcapp.lubrcapp.core.store.repository

import androidx.room.withTransaction
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.SyncQueueEntity
import org.json.JSONObject
import java.util.UUID
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import com.lubrcapp.lubrcapp.core.store.db.dao.VpsBusinessRecordDao
import com.lubrcapp.lubrcapp.core.store.db.entity.VpsBusinessRecordEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first, tenant/business-scoped read repository for operational VPS replicas.
 * Product, customer, order, inventory and financial feature layers can consume the
 * same durable Room records that the incoming synchronization transaction writes.
 *
 * Writes must use the domain command/queue path, never overwrite remote replicas
 * directly; otherwise offline edits could be lost.
 */
@Singleton
class RoomVpsBusinessRepository @Inject constructor(
    private val records: VpsBusinessRecordDao,
    private val database: AppDatabase,
) : OperationalBusinessRepository {
    override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
        observe(scope.tenantId, scope.businessId, table).map { rows -> rows.filterNot { JSONObject(it.payloadJson).optBoolean("_deleted", false) }.map { OperationalRecord(it.entityId, it.payloadJson) } }

    override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
        list(scope.tenantId, scope.businessId, table).filterNot { JSONObject(it.payloadJson).optBoolean("_deleted", false) }.map { OperationalRecord(it.entityId, it.payloadJson) }

    override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) =
        saveOffline(SyncIdentity(scope.tenantId, scope.businessId, scope.deviceId), table, id, json, revision)

    private val deletableTypes = setOf("products", "services", "customers", "vehicles", "vendors", "reminders", "price_lists", "price_list_items", "party_price_lists", "party_prices", "customer_groups", "units", "unit_conversions", "warehouses", "warehouse_locations",
        // Nonfinancial master data the items screens delete by tombstone, like products:
        // categories, brands, branches and batches are record types the Phase 15/16
        // features own, and their screens call delete on them.
        "categories", "brands", "branches", "batches")

    override suspend fun delete(scope: OperationalScope, table: String, id: String) {
        validate(scope.tenantId, scope.businessId, table)
        require(table in deletableTypes) { "Financial and inventory history requires a reversal, not deletion" }
        require(scope.deviceId.isNotBlank() && id.isNotBlank())
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = requireNotNull(records.get(scope.tenantId, scope.businessId, table, id)) { "Record does not exist in scope" }
            val tombstone = JSONObject(existing.payloadJson).put("_deleted", true).put("uuid", id).toString()
            records.upsert(existing.copy(payloadJson = tombstone, updatedAt = now))
            database.syncQueueDao().enqueue(SyncQueueEntity(
                id = UUID.randomUUID().toString(), tenantId = scope.tenantId,
                businessId = scope.businessId, entityType = table, entityId = id,
                operation = "DELETE", payloadJson = tombstone, revision = 0,
                status = "PENDING", createdAt = now, updatedAt = now, deviceId = scope.deviceId
            ))
        }
    }

    private val allowedTypes = setOf(
        "products", "services", "customers", "vehicles", "vendors",
        "orders", "order_items", "expenses", "accounts", "account_transactions",
        "purchase_orders", "vendor_payables", "reminders", "inspections",
        "cash_movements", "reservations", "party_ledger", "commitments",
        "stock_movements", "price_lists", "price_list_items", "party_price_lists",
        "party_prices", "customer_groups", "units", "unit_conversions",
        "warehouses", "warehouse_locations",
        // Record types later phases added without updating this list — the
        // screens that own them crashed on open (validate throws during
        // composition) and their saves were rejected outright:
        // items master data (15/16), inventory records (19), accounts
        // journal (20) and sales numbering/timeline (18).
        "categories", "brands", "branches", "batches",
        "stock_levels", "stock_transfers", "serial_numbers",
        "journal_entries", "timeline_entries", "numbering_sequences",
    )

    private fun validate(tenantId: String, businessId: String, entityType: String) {
        require(tenantId.isNotBlank() && businessId.isNotBlank()) {
            "Tenant and business are required"
        }
        require(entityType in allowedTypes) { "Unsupported operational entity type" }
    }

    fun observe(
        tenantId: String, businessId: String, entityType: String
    ): Flow<List<VpsBusinessRecordEntity>> {
        validate(tenantId, businessId, entityType)
        return records.observeByType(tenantId, businessId, entityType)
    }

    suspend fun list(
        tenantId: String, businessId: String, entityType: String
    ): List<VpsBusinessRecordEntity> {
        validate(tenantId, businessId, entityType)
        return records.listByType(tenantId, businessId, entityType)
    }

    /** Atomically persist an offline edit and its durable upload intent. */
    suspend fun saveOffline(
        identity: SyncIdentity, entityType: String, entityId: String,
        payloadJson: String, revision: Long = 0L,
    ) {
        validate(identity.tenantId, identity.businessId, entityType)
        require(identity.deviceId.isNotBlank() && entityId.isNotBlank())
        require(revision >= 0)
        val payload = JSONObject(payloadJson)
        // The record id is authoritative: typed codecs (ItemJson, PartyJson,
        // and friends) do not write a uuid key, so a missing uuid is
        // injected here rather than rejecting every typed save. An
        // explicitly mismatched uuid is still rejected, as before.
        if (payload.has("uuid")) {
            require(payload.getString("uuid") == entityId) { "Payload UUID mismatch" }
        } else {
            payload.put("uuid", entityId)
        }
        require(!payload.optBoolean("_deleted", false)) { "Cannot save a tombstone as a live record" }
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = records.get(identity.tenantId, identity.businessId, entityType, entityId)
            records.upsert(VpsBusinessRecordEntity(
                identity.tenantId, identity.businessId, entityType, entityId,
                payload.toString(), now,
            ))
            database.syncQueueDao().enqueue(SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                tenantId = identity.tenantId, businessId = identity.businessId,
                entityType = entityType, entityId = entityId,
                operation = if (existing == null) "CREATE" else "UPDATE",
                payloadJson = payload.toString(),
                revision = revision, status = "PENDING",
                createdAt = now, updatedAt = now, deviceId = identity.deviceId,
            ))
        }
    }

    suspend fun get(
        tenantId: String, businessId: String, entityType: String, entityId: String
    ): VpsBusinessRecordEntity? {
        validate(tenantId, businessId, entityType)
        require(entityId.isNotBlank())
        return records.get(tenantId, businessId, entityType, entityId)
    }
}
