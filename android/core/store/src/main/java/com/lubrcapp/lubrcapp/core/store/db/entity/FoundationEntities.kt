package com.lubrcapp.lubrcapp.core.store.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tenants",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["syncStatus"]),
        Index(value = ["deletedAt"]),
    ],
)
data class TenantEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "businesses",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("tenantId"),
        Index(value = ["tenantId", "name"]),
        Index(value = ["tenantId", "serverId"], unique = true),
        Index(value = ["tenantId", "syncStatus"]),
        Index(value = ["tenantId", "deletedAt"]),
    ],
)
data class BusinessEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val businessType: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val country: String = "",
    val postalCode: String = "",
    val website: String = "",
    val taxNumber: String? = null,
    val currencyCode: String,
    val timezoneId: String,
    val inviteCode: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"]),
        Index(value = ["phone"]),
        Index(value = ["serverId"], unique = true),
        Index(value = ["syncStatus"]),
        Index(value = ["deletedAt"]),
    ],
)
data class UserEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val email: String? = null,
    val isActive: Boolean = true,
    val lastLoginAt: Long? = null,
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "roles",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index(value = ["tenantId", "businessId", "name"], unique = true),
        Index(value = ["tenantId", "businessId", "deletedAt"]),
    ],
)
data class RoleEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val name: String,
    val isSystemTemplate: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "permissions",
    indices = [
        Index(value = ["permissionKey"], unique = true),
    ],
)
data class PermissionEntity(
    @androidx.room.PrimaryKey val id: String,
    val permissionKey: String,
    val description: String = "",
)

@Entity(
    tableName = "user_businesses",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index("userId"),
        Index(value = ["tenantId", "businessId", "userId"], unique = true),
        Index(value = ["tenantId", "businessId", "deletedAt"]),
    ],
)
data class UserBusinessEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val userId: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "user_business_roles",
    primaryKeys = ["tenantId", "businessId", "userBusinessId", "roleId"],
    foreignKeys = [
        ForeignKey(
            entity = UserBusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["userBusinessId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("userBusinessId"),
        Index("roleId"),
        Index(value = ["tenantId", "businessId"]),
    ],
)
data class UserBusinessRoleEntity(
    val tenantId: String,
    val businessId: String,
    val userBusinessId: String,
    val roleId: String,
)

@Entity(
    tableName = "role_permissions",
    primaryKeys = ["tenantId", "businessId", "roleId", "permissionId"],
    foreignKeys = [
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PermissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["permissionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("roleId"),
        Index("permissionId"),
        Index(value = ["tenantId", "businessId"]),
    ],
)
data class RolePermissionEntity(
    val tenantId: String,
    val businessId: String,
    val roleId: String,
    val permissionId: String,
)

@Entity(
    tableName = "settings",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index(value = ["tenantId", "businessId", "settingKey"], unique = true),
        Index(value = ["tenantId", "businessId", "deletedAt"]),
    ],
)
data class SettingEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val settingKey: String,
    val value: String,
    val valueType: String = "STRING",
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "feature_flags",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index(value = ["tenantId", "businessId", "featureKey"], unique = true),
        Index(value = ["tenantId", "businessId", "deletedAt"]),
    ],
)
data class FeatureFlagEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val featureKey: String,
    val enabled: Boolean,
    val source: String = "BUSINESS",
    val createdAt: Long,
    val createdBy: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deviceId: String,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index(value = ["tenantId", "businessId", "entityType", "entityId"]),
        Index(value = ["tenantId", "businessId", "createdAt"]),
        Index(value = ["tenantId", "businessId", "syncStatus"]),
    ],
)
data class AuditLogEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val actorUserId: String? = null,
    val deviceId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val reason: String? = null,
    val createdAt: Long,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "activity_logs",
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index(value = ["tenantId", "businessId", "createdAt"]),
        Index(value = ["tenantId", "businessId", "activityType"]),
        Index(value = ["tenantId", "businessId", "syncStatus"]),
    ],
)
data class ActivityLogEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val actorUserId: String? = null,
    val deviceId: String,
    val activityType: String,
    val subjectType: String? = null,
    val subjectId: String? = null,
    val detailsJson: String? = null,
    val createdAt: Long,
    val serverId: String? = null,
    val revision: Long = 0L,
    val syncStatus: String,
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index("tenantId"),
        Index("businessId"),
        Index(value = ["tenantId", "businessId", "status", "nextAttemptAt", "createdAt"]),
        Index(value = ["tenantId", "businessId", "entityType", "entityId"]),
    ],
)
data class SyncQueueEntity(
    @androidx.room.PrimaryKey val id: String,
    val tenantId: String,
    val businessId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String? = null,
    val revision: Long = 0L,
    val status: String = "PENDING",
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val nextAttemptAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String? = null,
    val deviceId: String,
)
