package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.core.repository.RefreshResult
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import kotlinx.coroutines.flow.Flow

fun interface DeploymentConfigSource {
    fun get(): DeploymentConfig
}

data class ConfigurationRefreshResult(
    val businesses: RefreshResult,
    val settings: RefreshResult,
    val featureFlags: RefreshResult,
)

interface WhiteLabelConfigService {
    fun getDeploymentConfig(): DeploymentConfig

    fun observeResolvedConfig(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<ResolvedWhiteLabelConfig>

    suspend fun getResolvedConfig(
        tenantId: TenantId,
        businessId: BusinessId,
    ): ResolvedWhiteLabelConfig

    suspend fun refreshBusinessConfig(
        tenantId: TenantId,
        businessId: BusinessId,
    ): ConfigurationRefreshResult
}
