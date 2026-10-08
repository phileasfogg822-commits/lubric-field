package com.lubrcapp.lubrcapp.core.store.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lubrcapp.lubrcapp.core.store.db.entity.ActivityLogEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.AuditLogEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.FeatureFlagEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.PermissionEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.RoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.RolePermissionEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SyncQueueEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessRoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Upsert
    suspend fun upsert(entity: TenantEntity)

    @Query("SELECT * FROM tenants WHERE id = :tenantId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(tenantId: String): TenantEntity?

    @Query("SELECT * FROM tenants WHERE deletedAt IS NULL AND isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<TenantEntity>>

    @Query(
        """
        UPDATE tenants
        SET deletedAt = :deletedAt, deletedBy = :deletedBy, updatedAt = :deletedAt,
            updatedBy = :deletedBy, revision = revision + 1, syncStatus = 'PENDING'
        WHERE id = :tenantId AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(tenantId: String, deletedAt: Long, deletedBy: String?): Int
}

@Dao
interface BusinessDao {
    @Upsert
    suspend fun upsert(entity: BusinessEntity)

    @Query(
        """
        SELECT * FROM businesses
        WHERE tenantId = :tenantId AND id = :businessId AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getById(tenantId: String, businessId: String): BusinessEntity?

    @Query(
        """
        SELECT * FROM businesses
        WHERE tenantId = :tenantId AND deletedAt IS NULL
        ORDER BY name
        """
    )
    fun observeByTenant(tenantId: String): Flow<List<BusinessEntity>>

    @Query(
        """
        UPDATE businesses
        SET deletedAt = :deletedAt, deletedBy = :deletedBy, updatedAt = :deletedAt,
            updatedBy = :deletedBy, revision = revision + 1, syncStatus = 'PENDING'
        WHERE tenantId = :tenantId AND id = :businessId AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(
        tenantId: String,
        businessId: String,
        deletedAt: Long,
        deletedBy: String?,
    ): Int
}

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(entity: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(userId: String): UserEntity?

    @Query(
        """
        UPDATE users
        SET deletedAt = :deletedAt, deletedBy = :deletedBy, updatedAt = :deletedAt,
            updatedBy = :deletedBy, revision = revision + 1, syncStatus = 'PENDING'
        WHERE id = :userId AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(userId: String, deletedAt: Long, deletedBy: String?): Int
}

@Dao
interface AccessDao {
    @Upsert
    suspend fun upsertUserBusiness(entity: UserBusinessEntity)

    @Upsert
    suspend fun upsertRole(entity: RoleEntity)

    @Upsert
    suspend fun upsertPermission(entity: PermissionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addUserBusinessRole(entity: UserBusinessRoleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRolePermission(entity: RolePermissionEntity)

    @Query(
        """
        SELECT * FROM user_businesses
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND userId = :userId AND deletedAt IS NULL AND isActive = 1
        LIMIT 1
        """
    )
    suspend fun getMembership(
        tenantId: String,
        businessId: String,
        userId: String,
    ): UserBusinessEntity?

    @Query(
        """
        SELECT DISTINCT p.permissionKey
        FROM permissions p
        INNER JOIN role_permissions rp ON rp.permissionId = p.id
        INNER JOIN roles r ON r.id = rp.roleId
        INNER JOIN user_business_roles ubr ON ubr.roleId = r.id
        INNER JOIN user_businesses ub ON ub.id = ubr.userBusinessId
        WHERE ub.tenantId = :tenantId
          AND ub.businessId = :businessId
          AND ub.userId = :userId
          AND ub.deletedAt IS NULL
          AND ub.isActive = 1
          AND r.deletedAt IS NULL
          AND r.isActive = 1
          AND rp.tenantId = :tenantId
          AND rp.businessId = :businessId
          AND ubr.tenantId = :tenantId
          AND ubr.businessId = :businessId
        ORDER BY p.permissionKey
        """
    )
    suspend fun getPermissionKeys(
        tenantId: String,
        businessId: String,
        userId: String,
    ): List<String>
}

@Dao
interface SettingsDao {
    @Upsert
    suspend fun upsert(entity: SettingEntity)

    @Query(
        """
        SELECT * FROM settings
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND settingKey = :key AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun get(
        tenantId: String,
        businessId: String,
        key: String,
    ): SettingEntity?

    @Query(
        """
        SELECT * FROM settings
        WHERE tenantId = :tenantId AND businessId = :businessId AND deletedAt IS NULL
        ORDER BY settingKey
        """
    )
    fun observeAll(tenantId: String, businessId: String): Flow<List<SettingEntity>>

    @Query(
        """
        UPDATE settings
        SET deletedAt = :deletedAt, deletedBy = :deletedBy, updatedAt = :deletedAt,
            updatedBy = :deletedBy, revision = revision + 1, syncStatus = 'PENDING'
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND settingKey = :key AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(
        tenantId: String,
        businessId: String,
        key: String,
        deletedAt: Long,
        deletedBy: String?,
    ): Int
}

@Dao
interface FeatureFlagDao {
    @Upsert
    suspend fun upsert(entity: FeatureFlagEntity)

    @Query(
        """
        SELECT * FROM feature_flags
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND featureKey = :key AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun get(
        tenantId: String,
        businessId: String,
        key: String,
    ): FeatureFlagEntity?

    @Query(
        """
        SELECT * FROM feature_flags
        WHERE tenantId = :tenantId AND businessId = :businessId AND deletedAt IS NULL
        ORDER BY featureKey
        """
    )
    fun observeAll(tenantId: String, businessId: String): Flow<List<FeatureFlagEntity>>
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AuditLogEntity)

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE tenantId = :tenantId AND businessId = :businessId
        ORDER BY createdAt DESC
        """
    )
    fun observeByBusiness(
        tenantId: String,
        businessId: String,
    ): Flow<List<AuditLogEntity>>

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND entityType = :entityType AND entityId = :entityId
        ORDER BY createdAt DESC
        """
    )
    suspend fun getForEntity(
        tenantId: String,
        businessId: String,
        entityType: String,
        entityId: String,
    ): List<AuditLogEntity>
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ActivityLogEntity)

    @Query(
        """
        SELECT * FROM activity_logs
        WHERE tenantId = :tenantId AND businessId = :businessId
        ORDER BY createdAt DESC
        """
    )
    fun observeByBusiness(
        tenantId: String,
        businessId: String,
    ): Flow<List<ActivityLogEntity>>
}

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(entity: SyncQueueEntity)

    @Query(
        """
        SELECT * FROM sync_queue
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND status IN (:statuses)
          AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun nextBatch(
        tenantId: String,
        businessId: String,
        statuses: List<String>,
        now: Long,
        limit: Int,
    ): List<SyncQueueEntity>

    @Query(
        """
        UPDATE sync_queue
        SET status = :status,
            attemptCount = :attemptCount,
            lastError = :lastError,
            nextAttemptAt = :nextAttemptAt,
            updatedAt = :updatedAt
        WHERE id = :id AND tenantId = :tenantId AND businessId = :businessId
        """
    )
    suspend fun updateState(
        tenantId: String,
        businessId: String,
        id: String,
        status: String,
        attemptCount: Int,
        lastError: String?,
        nextAttemptAt: Long?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_queue
        WHERE tenantId = :tenantId AND businessId = :businessId AND status = :status
        """
    )
    suspend fun countByStatus(
        tenantId: String,
        businessId: String,
        status: String,
    ): Int

    @Query(
        """
        DELETE FROM sync_queue
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :id AND status = 'SYNCED'
        """
    )
    suspend fun deleteSynced(tenantId: String, businessId: String, id: String): Int
}
