package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 30.1 — multi-tenancy guards (§186).
 * Every business-owned record carries a tenant
 * id and a business id; a scope admits only
 * records carrying exactly its pair. These are
 * pure functions over plain data: the app-side
 * contract for §186. The spec requires the
 * BACKEND to enforce tenant isolation (never
 * frontend filtering alone), so this guard is
 * the contract the backend implements — it is
 * not, by itself, the enforcement.
 */
data class TenantScope(
    val tenantId: String,
    val businessId: String,
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
        require(businessId.isNotBlank()) { "businessId must not be blank" }
    }
}

data class TenantScopedRecord(
    val id: String,
    val tenantId: String,
    val businessId: String,
)

object TenantScopeOps {
    fun isVisible(record: TenantScopedRecord, scope: TenantScope): Boolean =
        record.tenantId == scope.tenantId && record.businessId == scope.businessId

    fun requireVisible(record: TenantScopedRecord, scope: TenantScope) {
        require(isVisible(record, scope)) {
            "Record ${record.id} is outside scope ${scope.tenantId}/${scope.businessId}"
        }
    }

    fun visible(records: List<TenantScopedRecord>, scope: TenantScope): List<TenantScopedRecord> =
        records.filter { isVisible(it, scope) }
}
