package com.lubrcapp.lubrcapp.core.network.repository

import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteDataSourceModule {
    @Binds @Singleton
    abstract fun bindBusinessRemote(impl: DeferredRemoteDataSource): BusinessRemoteDataSource

    @Binds @Singleton
    abstract fun bindUserRemote(impl: DeferredRemoteDataSource): UserRemoteDataSource

    @Binds @Singleton
    abstract fun bindSettingsRemote(impl: DeferredRemoteDataSource): SettingsRemoteDataSource

    @Binds @Singleton
    abstract fun bindConfigurationRemote(
        impl: DeferredRemoteDataSource,
    ): ConfigurationRemoteDataSource
}
