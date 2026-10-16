package com.lubrcapp.lubrcapp.core.repository

import kotlinx.coroutines.flow.Flow

/** Tenant-scoped operational records, persisted locally and queued for optional sync. */
data class OperationalRecord(val id: String, val json: String)
data class OperationalScope(val tenantId: String, val businessId: String, val deviceId: String)

interface OperationalBusinessRepository {
    fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>>
    suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord>
    suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long = 0)
}
