package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

data class AuditEvent(
    val id: String,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val actorUserId: String?,
    val deviceId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val beforeJson: String?,
    val afterJson: String?,
    val reason: String?,
    val createdAt: Long,
)

object AuditActions {
    const val BUSINESS_CREATED = "security.business.created"
    const val USER_CREATED = "security.user.created"
    const val MEMBERSHIP_ACTIVE_CHANGED = "security.membership.active_changed"
    const val ROLE_CREATED = "security.role.created"
    const val ROLE_ACTIVE_CHANGED = "security.role.active_changed"
    const val ROLE_CAPABILITIES_CHANGED = "security.role.capabilities_changed"
    const val ROLE_ASSIGNMENTS_CHANGED = "security.role.assignments_changed"
    const val AUTHORIZATION_DENIED = "security.authorization.denied"
}


/**
 * Read-only audit access must be tenant scoped and authorized at the use-case boundary.
 * Consumers must not receive unrestricted DAO access or expose raw audit payloads in logs.
 */
interface AuditRepository {
    fun observeRecent(
        tenantId: TenantId,
        businessId: BusinessId,
        limit: Int = 100,
    ): Flow<List<AuditEvent>>

    suspend fun forEntity(
        tenantId: TenantId,
        businessId: BusinessId,
        entityType: String,
        entityId: String,
    ): List<AuditEvent>
}

class ObserveAuditEventsUseCase @Inject constructor(
    private val repository: AuditRepository,
    private val authorization: com.lubrcapp.lubrcapp.core.repository.usecase.AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        limit: Int = 100,
    ): Flow<List<AuditEvent>> {
        authorization.require(actor, Capabilities.AUDIT_VIEW)
        return repository.observeRecent(
            actor.tenantId,
            actor.businessId,
            limit.coerceIn(1, 500),
        )
    }
}

class GetEntityAuditUseCase @Inject constructor(
    private val repository: AuditRepository,
    private val authorization: com.lubrcapp.lubrcapp.core.repository.usecase.AuthorizationService,
) {
    suspend operator fun invoke(
        actor: TenantContext,
        entityType: String,
        entityId: String,
    ): List<AuditEvent> {
        authorization.require(actor, Capabilities.AUDIT_VIEW)
        return repository.forEntity(
            actor.tenantId,
            actor.businessId,
            entityType,
            entityId,
        )
    }
}
