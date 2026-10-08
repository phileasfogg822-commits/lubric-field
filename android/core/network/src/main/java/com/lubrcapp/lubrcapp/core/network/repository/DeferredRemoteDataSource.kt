package com.lubrcapp.lubrcapp.core.network.repository

import com.lubrcapp.lubrcapp.core.repository.BusinessFeatureFlag
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.core.repository.RemoteResult
import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.ConfigurationRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsRemoteDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserRemoteDataSource
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Honest Phase-4 remote boundary.
 *
 * Repositories already have a remote-data-source contract, but the actual API
 * endpoints and synchronization protocol are intentionally not invented here.
 * Phase 12/30 will replace this implementation with authenticated network data
 * sources. Normal writes remain local-first and therefore fully offline.
 */
@Singleton
class DeferredRemoteDataSource @Inject constructor() :
    BusinessRemoteDataSource,
    UserRemoteDataSource,
    SettingsRemoteDataSource,
    ConfigurationRemoteDataSource {

    private fun unavailable(): RemoteResult.Unavailable =
        RemoteResult.Unavailable("Remote data source is not configured yet")

    override suspend fun fetchBusinesses(tenantId: TenantId): RemoteResult<List<Business>> =
        unavailable()

    override suspend fun fetchUser(userId: UserId): RemoteResult<User> =
        unavailable()

    override suspend fun fetchSettings(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RemoteResult<List<BusinessSetting>> = unavailable()

    override suspend fun fetchFeatureFlags(
        tenantId: TenantId,
        businessId: BusinessId,
    ): RemoteResult<List<BusinessFeatureFlag>> = unavailable()
}
