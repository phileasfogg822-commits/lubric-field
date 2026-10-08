package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.core.repository.BusinessRepository
import com.lubrcapp.lubrcapp.core.repository.ConfigurationRepository
import com.lubrcapp.lubrcapp.core.repository.SettingsRepository
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@Singleton
class WhiteLabelConfigServiceImpl @Inject constructor(
    private val deploymentSource: DeploymentConfigSource,
    private val businessRepository: BusinessRepository,
    private val settingsRepository: SettingsRepository,
    private val configurationRepository: ConfigurationRepository,
    private val parser: BusinessConfigurationParser,
    private val resolver: WhiteLabelConfigResolver,
) : WhiteLabelConfigService {

    override fun getDeploymentConfig(): DeploymentConfig = deploymentSource.get()

    override fun observeResolvedConfig(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<ResolvedWhiteLabelConfig> =
        combine(
            businessRepository.observeBusinesses(tenantId),
            settingsRepository.observeSettings(tenantId, businessId),
            configurationRepository.observeFeatureFlags(tenantId, businessId),
        ) { businesses, settings, flags ->
            val business = businesses.firstOrNull { it.id == businessId }
                ?: error(
                    "Business ${businessId.value} is not available for tenant ${tenantId.value}"
                )
            resolver.resolve(
                deployment = deploymentSource.get(),
                business = parser.parse(business, settings, flags),
            )
        }.distinctUntilChanged()

    override suspend fun getResolvedConfig(
        tenantId: TenantId,
        businessId: BusinessId,
    ): ResolvedWhiteLabelConfig {
        val business = requireNotNull(
            businessRepository.getBusiness(tenantId, businessId)
        ) {
            "Business ${businessId.value} is not available for tenant ${tenantId.value}"
        }
        val settings = settingsRepository.observeSettings(tenantId, businessId).first()
        val flags = configurationRepository.observeFeatureFlags(tenantId, businessId).first()
        return resolver.resolve(
            deployment = deploymentSource.get(),
            business = parser.parse(business, settings, flags),
        )
    }

    override suspend fun refreshBusinessConfig(
        tenantId: TenantId,
        businessId: BusinessId,
    ): ConfigurationRefreshResult = ConfigurationRefreshResult(
        businesses = businessRepository.refreshBusinesses(tenantId),
        settings = settingsRepository.refreshSettings(tenantId, businessId),
        featureFlags = configurationRepository.refreshFeatureFlags(tenantId, businessId),
    )
}
