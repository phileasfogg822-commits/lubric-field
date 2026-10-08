package com.lubrcapp.lubrcapp.core.store.db

import android.content.Context
import androidx.room.Room
import com.lubrcapp.lubrcapp.core.store.db.dao.AccessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.ActivityDao
import com.lubrcapp.lubrcapp.core.store.db.dao.AuditDao
import com.lubrcapp.lubrcapp.core.store.db.dao.BusinessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.FeatureFlagDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SettingsDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SyncQueueDao
import com.lubrcapp.lubrcapp.core.store.db.dao.TenantDao
import com.lubrcapp.lubrcapp.core.store.db.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    @Provides fun provideTenantDao(database: AppDatabase): TenantDao = database.tenantDao()
    @Provides fun provideBusinessDao(database: AppDatabase): BusinessDao = database.businessDao()
    @Provides fun provideUserDao(database: AppDatabase): UserDao = database.userDao()
    @Provides fun provideAccessDao(database: AppDatabase): AccessDao = database.accessDao()
    @Provides fun provideSettingsDao(database: AppDatabase): SettingsDao = database.settingsDao()
    @Provides fun provideFeatureFlagDao(database: AppDatabase): FeatureFlagDao = database.featureFlagDao()
    @Provides fun provideAuditDao(database: AppDatabase): AuditDao = database.auditDao()
    @Provides fun provideActivityDao(database: AppDatabase): ActivityDao = database.activityDao()
    @Provides fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao = database.syncQueueDao()
}
