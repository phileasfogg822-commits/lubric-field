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

    @Query("DELETE FROM tenants WHERE id = :tenantId")
    suspend fun deleteById(tenantId: String): Int

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
        WHERE tenantId = :tenantId AND id = :businessId
          AND deletedAt IS NULL AND isActive = 1
        LIMIT 1
        """
    )
    suspend fun getActiveById(
        tenantId: String,
        businessId: String,
    ): BusinessEntity?

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
        SELECT * FROM businesses
        WHERE deletedAt IS NULL AND isActive = 1
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    fun observeFirstActive(): Flow<BusinessEntity?>

    @Query(
        """
        SELECT * FROM businesses
        WHERE deletedAt IS NULL AND isActive = 1
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun getFirstActive(): BusinessEntity?

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

    @Query("DELETE FROM businesses WHERE tenantId = :tenantId AND id = :businessId")
    suspend fun deleteById(tenantId: String, businessId: String): Int
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

    @Query("DELETE FROM user_businesses WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteMembershipsByBusiness(tenantId: String, businessId: String): Int

    @Query("DELETE FROM user_business_roles WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteUserBusinessRolesByBusiness(tenantId: String, businessId: String): Int

    @Query("DELETE FROM roles WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteRolesByBusiness(tenantId: String, businessId: String): Int

    @Query("DELETE FROM role_permissions WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteRolePermissionsByBusiness(tenantId: String, businessId: String): Int

    @Query(
        """
        SELECT b.* FROM businesses b
        INNER JOIN user_businesses ub
          ON ub.tenantId = b.tenantId
         AND ub.businessId = b.id
        WHERE ub.userId = :userId
          AND ub.deletedAt IS NULL
          AND ub.isActive = 1
          AND b.deletedAt IS NULL
          AND b.isActive = 1
        ORDER BY b.name, b.id
        """
    )
    fun observeBusinessesForUser(userId: String): Flow<List<BusinessEntity>>

    @Query(
        """
        SELECT b.* FROM businesses b
        INNER JOIN user_businesses ub
          ON ub.tenantId = b.tenantId
         AND ub.businessId = b.id
        WHERE ub.userId = :userId
          AND ub.deletedAt IS NULL
          AND ub.isActive = 1
          AND b.deletedAt IS NULL
          AND b.isActive = 1
        ORDER BY b.name, b.id
        """
    )
    suspend fun getBusinessesForUser(userId: String): List<BusinessEntity>

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


    @Query(
        """
        SELECT * FROM user_businesses
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND deletedAt IS NULL
        ORDER BY createdAt, id
        """
    )
    fun observeMemberships(
        tenantId: String,
        businessId: String,
    ): Flow<List<UserBusinessEntity>>

    @Query(
        """
        SELECT * FROM user_businesses
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :membershipId AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getMembershipById(
        tenantId: String,
        businessId: String,
        membershipId: String,
    ): UserBusinessEntity?

    @Query(
        """
        UPDATE user_businesses
        SET isActive = :active,
            updatedAt = :updatedAt,
            updatedBy = :updatedBy,
            revision = revision + 1,
            syncStatus = 'PENDING'
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :membershipId AND deletedAt IS NULL
        """
    )
    suspend fun setMembershipActive(
        tenantId: String,
        businessId: String,
        membershipId: String,
        active: Boolean,
        updatedAt: Long,
        updatedBy: String?,
    ): Int

    // Refresh touch: capability edits and role assignments change only
    // the link tables (role_permissions, user_business_roles), which the
    // roles/memberships Flows do not observe — without a touch on the
    // row itself, the list never re-emits and the UI keeps stale
    // capabilities/roleIds after a silent, successful save.
    @Query(
        """
        UPDATE roles
        SET updatedAt = :updatedAt,
            updatedBy = :updatedBy,
            revision = revision + 1
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :roleId AND deletedAt IS NULL
        """
    )
    suspend fun touchRole(
        tenantId: String,
        businessId: String,
        roleId: String,
        updatedAt: Long,
        updatedBy: String?,
    ): Int

    @Query(
        """
        UPDATE user_businesses
        SET updatedAt = :updatedAt,
            updatedBy = :updatedBy,
            revision = revision + 1
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :membershipId AND deletedAt IS NULL
        """
    )
    suspend fun touchMembership(
        tenantId: String,
        businessId: String,
        membershipId: String,
        updatedAt: Long,
        updatedBy: String?,
    ): Int

    @Query(
        """
        SELECT * FROM roles
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND deletedAt IS NULL
        ORDER BY name, id
        """
    )
    fun observeRoles(
        tenantId: String,
        businessId: String,
    ): Flow<List<RoleEntity>>

    @Query(
        """
        SELECT * FROM roles
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :roleId AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getRole(
        tenantId: String,
        businessId: String,
        roleId: String,
    ): RoleEntity?


    @Query(
        """
        UPDATE roles
        SET isActive = :active,
            updatedAt = :updatedAt,
            updatedBy = :updatedBy,
            revision = revision + 1,
            syncStatus = 'PENDING'
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :roleId AND deletedAt IS NULL
        """
    )
    suspend fun setRoleActive(
        tenantId: String,
        businessId: String,
        roleId: String,
        active: Boolean,
        updatedAt: Long,
        updatedBy: String?,
    ): Int

    @Query("SELECT * FROM permissions WHERE permissionKey = :key LIMIT 1")
    suspend fun getPermissionByKey(key: String): PermissionEntity?

    @Query(
        """
        SELECT p.permissionKey
        FROM permissions p
        INNER JOIN role_permissions rp ON rp.permissionId = p.id
        WHERE rp.tenantId = :tenantId
          AND rp.businessId = :businessId
          AND rp.roleId = :roleId
        ORDER BY p.permissionKey
        """
    )
    suspend fun getRolePermissionKeys(
        tenantId: String,
        businessId: String,
        roleId: String,
    ): List<String>

    @Query(
        """
        DELETE FROM role_permissions
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND roleId = :roleId
        """
    )
    suspend fun clearRolePermissions(
        tenantId: String,
        businessId: String,
        roleId: String,
    ): Int

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
    fun observePermissionKeys(
        tenantId: String,
        businessId: String,
        userId: String,
    ): Flow<List<String>>

    @Query(
        """
        SELECT ubr.roleId
        FROM user_business_roles ubr
        INNER JOIN user_businesses ub ON ub.id = ubr.userBusinessId
        INNER JOIN roles r ON r.id = ubr.roleId
        WHERE ub.tenantId = :tenantId
          AND ub.businessId = :businessId
          AND ub.userId = :userId
          AND ub.deletedAt IS NULL
          AND ub.isActive = 1
          AND ubr.tenantId = :tenantId
          AND ubr.businessId = :businessId
          AND r.tenantId = :tenantId
          AND r.businessId = :businessId
          AND r.deletedAt IS NULL
          AND r.isActive = 1
        ORDER BY ubr.roleId
        """
    )
    fun observeRoleIdsForUser(
        tenantId: String,
        businessId: String,
        userId: String,
    ): Flow<List<String>>

    @Query(
        """
        SELECT ubr.roleId
        FROM user_business_roles ubr
        WHERE ubr.tenantId = :tenantId
          AND ubr.businessId = :businessId
          AND ubr.userBusinessId = :userBusinessId
        ORDER BY ubr.roleId
        """
    )
    suspend fun getRoleIds(
        tenantId: String,
        businessId: String,
        userBusinessId: String,
    ): List<String>

    @Query(
        """
        DELETE FROM user_business_roles
        WHERE tenantId = :tenantId
          AND businessId = :businessId
          AND userBusinessId = :userBusinessId
        """
    )
    suspend fun clearUserBusinessRoles(
        tenantId: String,
        businessId: String,
        userBusinessId: String,
    ): Int
}

@Dao
interface SettingsDao {
    @Query("DELETE FROM settings WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteByScope(tenantId: String, businessId: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SettingEntity)

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
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(
        tenantId: String,
        businessId: String,
        limit: Int,
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
        SELECT * FROM sync_queue
        WHERE tenantId = :tenantId AND businessId = :businessId
          AND id = :id
        LIMIT 1
        """
    )
    suspend fun findById(tenantId: String, businessId: String, id: String): SyncQueueEntity?

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

    @Query("DELETE FROM sync_queue WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteByScope(tenantId: String, businessId: String): Int
}
