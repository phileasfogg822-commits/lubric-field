package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lubrcapp.lubrcapp.core.store.db.dao.AccessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.ActivityDao
import com.lubrcapp.lubrcapp.core.store.db.dao.AuditDao
import com.lubrcapp.lubrcapp.core.store.db.dao.BusinessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.FeatureFlagDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SettingsDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SyncQueueDao
import com.lubrcapp.lubrcapp.core.store.db.dao.TenantDao
import com.lubrcapp.lubrcapp.core.store.db.dao.UserDao
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

@Database(
    entities = [
        TenantEntity::class,
        BusinessEntity::class,
        UserEntity::class,
        RoleEntity::class,
        PermissionEntity::class,
        UserBusinessEntity::class,
        UserBusinessRoleEntity::class,
        RolePermissionEntity::class,
        SettingEntity::class,
        FeatureFlagEntity::class,
        AuditLogEntity::class,
        ActivityLogEntity::class,
        SyncQueueEntity::class,
    ],
    version = DatabaseMigrations.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(DomainTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tenantDao(): TenantDao
    abstract fun businessDao(): BusinessDao
    abstract fun userDao(): UserDao
    abstract fun accessDao(): AccessDao
    abstract fun settingsDao(): SettingsDao
    abstract fun featureFlagDao(): FeatureFlagDao
    abstract fun auditDao(): AuditDao
    abstract fun activityDao(): ActivityDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "business_os.db"
    }
}
