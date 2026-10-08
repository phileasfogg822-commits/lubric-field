package com.lubrcapp.lubrcapp.whitelist.config

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WhiteLabelConfigModule {
    @Binds
    @Singleton
    abstract fun bindWhiteLabelConfigService(
        implementation: WhiteLabelConfigServiceImpl,
    ): WhiteLabelConfigService
}
