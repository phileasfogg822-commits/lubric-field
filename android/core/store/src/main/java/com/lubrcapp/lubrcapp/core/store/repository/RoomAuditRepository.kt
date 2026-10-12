package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.repository.AuditEvent
import com.lubrcapp.lubrcapp.core.repository.AuditRepository
import com.lubrcapp.lubrcapp.core.store.db.dao.AuditDao
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomAuditRepository @Inject constructor(
    private val auditDao: AuditDao,
) : AuditRepository {

    override fun observeRecent(
        tenantId: TenantId,
        businessId: BusinessId,
        limit: Int,
    ): Flow<List<AuditEvent>> =
        auditDao.observeRecent(
            tenantId = tenantId.value,
            businessId = businessId.value,
            limit = limit.coerceIn(1, 500),
        ).map { events -> events.map { it.toDomain() } }

    override suspend fun forEntity(
        tenantId: TenantId,
        businessId: BusinessId,
        entityType: String,
        entityId: String,
    ): List<AuditEvent> =
        auditDao.getForEntity(
            tenantId = tenantId.value,
            businessId = businessId.value,
            entityType = entityType,
            entityId = entityId,
        ).map { it.toDomain() }

    private fun com.lubrcapp.lubrcapp.core.store.db.entity.AuditLogEntity.toDomain() =
        AuditEvent(
            id = id,
            tenantId = TenantId(tenantId),
            businessId = BusinessId(businessId),
            actorUserId = actorUserId,
            deviceId = deviceId,
            entityType = entityType,
            entityId = entityId,
            action = action,
            beforeJson = beforeJson,
            afterJson = afterJson,
            reason = reason,
            createdAt = createdAt,
        )
}
