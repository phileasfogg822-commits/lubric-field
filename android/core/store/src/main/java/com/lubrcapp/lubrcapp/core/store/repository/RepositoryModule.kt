package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.repository.BusinessRepository
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.ConfigurationRepository
import com.lubrcapp.lubrcapp.core.repository.DefaultBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.DefaultConfigurationRepository
import com.lubrcapp.lubrcapp.core.repository.DefaultSettingsRepository
import com.lubrcapp.lubrcapp.core.repository.DefaultSyncRepository
import com.lubrcapp.lubrcapp.core.repository.DefaultUserRepository
import com.lubrcapp.lubrcapp.core.repository.SettingsRepository
import com.lubrcapp.lubrcapp.core.repository.SyncRepository
import com.lubrcapp.lubrcapp.core.repository.UserRepository
import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SyncQueueLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalDataSourceModule {
    @Binds @Singleton
    abstract fun bindBusinessSetupRepository(
        impl: RoomBusinessSetupRepository,
    ): BusinessSetupRepository

    @Binds @Singleton
    abstract fun bindDeviceIdentityProvider(
        impl: PersistentDeviceIdentityProvider,
    ): DeviceIdentityProvider

    @Binds @Singleton
    abstract fun bindBusinessLocal(impl: RoomBusinessLocalDataSource): BusinessLocalDataSource

    @Binds @Singleton
    abstract fun bindUserLocal(impl: RoomUserLocalDataSource): UserLocalDataSource

    @Binds @Singleton
    abstract fun bindSettingsLocal(impl: RoomSettingsLocalDataSource): SettingsLocalDataSource

    @Binds @Singleton
    abstract fun bindConfigurationLocal(
        impl: RoomConfigurationLocalDataSource,
    ): ConfigurationLocalDataSource

    @Binds @Singleton
    abstract fun bindSyncQueueLocal(impl: RoomSyncQueueLocalDataSource): SyncQueueLocalDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideBusinessRepository(
        local: BusinessLocalDataSource,
        remote: BusinessRemoteDataSource,
    ): BusinessRepository = DefaultBusinessRepository(local, remote)

    @Provides
    @Singleton
    fun provideUserRepository(
        local: UserLocalDataSource,
        remote: UserRemoteDataSource,
    ): UserRepository = DefaultUserRepository(local, remote)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        local: SettingsLocalDataSource,
        remote: SettingsRemoteDataSource,
    ): SettingsRepository = DefaultSettingsRepository(local, remote)

    @Provides
    @Singleton
    fun provideConfigurationRepository(
        local: ConfigurationLocalDataSource,
        remote: ConfigurationRemoteDataSource,
    ): ConfigurationRepository = DefaultConfigurationRepository(local, remote)

    @Provides
    @Singleton
    fun provideSyncRepository(
        local: SyncQueueLocalDataSource,
    ): SyncRepository = DefaultSyncRepository(local)
}
