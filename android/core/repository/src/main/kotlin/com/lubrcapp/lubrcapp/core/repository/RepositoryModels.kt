package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.FeatureFlagId
import com.lubrcapp.lubrcapp.domain.common.SettingId
import com.lubrcapp.lubrcapp.domain.common.SyncOperationId
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Unavailable(val reason: String) : RemoteResult<Nothing>
    data class Failure(
        val message: String,
        val retryable: Boolean,
    ) : RemoteResult<Nothing>
}

sealed interface RefreshResult {
    data class Updated(val itemCount: Int) : RefreshResult
    data class Skipped(val reason: String) : RefreshResult
    data class Failed(
        val message: String,
        val retryable: Boolean,
    ) : RefreshResult
}

data class BusinessSetting(
    val id: SettingId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val key: String,
    val value: String,
    val valueType: String = "STRING",
    val metadata: EntityMetadata,
)

data class BusinessFeatureFlag(
    val id: FeatureFlagId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val key: String,
    val enabled: Boolean,
    val source: String = "BUSINESS",
    val metadata: EntityMetadata,
)

enum class SyncOperationKind {
    CREATE,
    UPDATE,
    DELETE,
}

data class SyncQueueItem(
    val id: SyncOperationId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val entityType: String,
    val entityId: String,
    val operation: SyncOperationKind,
    val payloadJson: String? = null,
    val revision: Long = 0L,
    val status: SyncStatus = SyncStatus.PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val nextAttemptAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val userId: UserId? = null,
    val deviceId: String,
)

data class SyncQueueSummary(
    val pending: Int,
    val syncing: Int,
    val failed: Int,
    val conflicts: Int,
)
