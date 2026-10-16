package com.lubrcapp.lubrcapp.core.store.repository

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
) {
    private val allowedTypes = setOf(
        "products", "services", "customers", "vehicles", "vendors",
        "orders", "order_items", "expenses", "accounts", "account_transactions",
        "purchase_orders", "vendor_payables", "reminders", "inspections",
        "cash_movements", "reservations", "party_ledger", "commitments",
        "stock_movements", "price_lists", "price_list_items", "party_price_lists",
        "party_prices", "customer_groups", "units", "unit_conversions",
        "warehouses", "warehouse_locations"
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

    suspend fun get(
        tenantId: String, businessId: String, entityType: String, entityId: String
    ): VpsBusinessRecordEntity? {
        validate(tenantId, businessId, entityType)
        require(entityId.isNotBlank())
        return records.get(tenantId, businessId, entityType, entityId)
    }
}
