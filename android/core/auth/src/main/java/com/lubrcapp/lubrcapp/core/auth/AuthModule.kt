package com.lubrcapp.lubrcapp.core.auth

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSecureAuthStorage(
        implementation: AndroidKeystoreSecureAuthStorage,
    ): SecureAuthStorage

    @Binds
    @Singleton
    abstract fun bindTokenStore(
        implementation: SecureTokenStore,
    ): TokenStore

    @Binds
    @Singleton
    abstract fun bindLocalAuthGateStore(
        implementation: SecureLocalAuthGateStore,
    ): LocalAuthGateStore

    @Binds
    @Singleton
    abstract fun bindSessionController(
        implementation: SessionManager,
    ): AuthSessionController

    @Binds
    @Singleton
    abstract fun bindSecurityPolicyStore(
        implementation: SecureSecurityPolicyStore,
    ): SecurityPolicyStore

    @Binds
    @Singleton
    abstract fun bindAccessTokenProvider(
        implementation: SessionManager,
    ): SessionAccessTokenProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AuthRuntimeModule {
    @Provides
    @Singleton
    fun provideAuthClock(): AuthClock = AuthClock { Instant.now() }
}
