package com.lubrcapp.lubrcapp.domain.common

import java.time.Instant

data class AuditMetadata(
    val createdAt: Instant,
    val createdBy: UserId? = null,
    val updatedAt: Instant = createdAt,
    val updatedBy: UserId? = createdBy,
) {
    init {
        require(!updatedAt.isBefore(createdAt)) {
            "updatedAt cannot be before createdAt"
        }
    }
}

data class SoftDeleteMetadata(
    val deletedAt: Instant? = null,
    val deletedBy: UserId? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    CONFLICT,
}

data class SyncMetadata(
    val deviceId: DeviceId,
    val serverId: String? = null,
    val revision: Long = 0L,
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val lastSyncedAt: Instant? = null,
) {
    init {
        require(revision >= 0L) { "Revision cannot be negative" }
        require(serverId == null || serverId.isNotBlank()) {
            "serverId cannot be blank when present"
        }
    }
}

data class EntityMetadata(
    val audit: AuditMetadata,
    val deletion: SoftDeleteMetadata = SoftDeleteMetadata(),
    val sync: SyncMetadata,
) {
    val isDeleted: Boolean get() = deletion.isDeleted

    companion object {
        fun newLocal(
            now: Instant,
            deviceId: DeviceId,
            actor: UserId? = null,
        ): EntityMetadata = EntityMetadata(
            audit = AuditMetadata(
                createdAt = now,
                createdBy = actor,
            ),
            sync = SyncMetadata(
                deviceId = deviceId,
                status = SyncStatus.LOCAL_ONLY,
            ),
        )
    }
}
