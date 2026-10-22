package com.lubrcapp.lubrcapp

import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.shell.AppModuleRegistry
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Shell gating source (Phase 13.2): the view model is a thin, failure-safe
 * window on the production [FeatureFlagService]. It must forward the
 * resolved enabled-module set for exactly the business the shell asks
 * about, pass live configuration updates through, and degrade to null —
 * the shell's "no gating" value — when resolution fails, so a
 * configuration problem can never hide modules.
 */
class ShellConfigViewModelTest {

    private class FakeFeatureFlagService(
        private val features: (TenantId, BusinessId) -> Flow<Set<String>>,
    ) : FeatureFlagService {
        val queries = mutableListOf<Pair<TenantId, BusinessId>>()

        override fun observeEnabledFeatures(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Flow<Set<String>> {
            queries += tenantId to businessId
            return features(tenantId, businessId)
        }

        override suspend fun isEnabled(
            tenantId: TenantId,
            businessId: BusinessId,
            feature: String,
        ): Boolean = features(tenantId, businessId).first().contains(feature)

        override fun getAllFeatureKeys(): Set<String> = FeatureFlagKeys.ALL
    }

    private fun context(tenant: String, business: String) = TenantContext(
        tenantId = TenantId(tenant),
        businessId = BusinessId(business),
        userId = UserId("owner-1"),
    )

    @Test
    fun forwardsTheResolvedSetForTheRequestedBusiness() = runBlocking {
        val service = FakeFeatureFlagService { _, business ->
            flowOf(
                when (business) {
                    BusinessId("business-a") -> setOf(
                        FeatureFlagKeys.MODULE_INVENTORY,
                        FeatureFlagKeys.MODULE_ORDERS,
                    )
                    else -> setOf(FeatureFlagKeys.MODULE_SYNC)
                },
            )
        }
        val viewModel = ShellConfigViewModel(service)

        assertEquals(
            setOf(FeatureFlagKeys.MODULE_INVENTORY, FeatureFlagKeys.MODULE_ORDERS),
            viewModel.enabledModuleKeys(context("tenant-a", "business-a")).first(),
        )
        // A different business is a different resolution: the shell
        // re-queries per selected business instead of reusing a stale set.
        assertEquals(
            setOf(FeatureFlagKeys.MODULE_SYNC),
            viewModel.enabledModuleKeys(context("tenant-a", "business-b")).first(),
        )
        assertEquals(
            listOf(
                TenantId("tenant-a") to BusinessId("business-a"),
                TenantId("tenant-a") to BusinessId("business-b"),
            ),
            service.queries,
        )
    }

    @Test
    fun liveConfigurationUpdatesFlowThrough() = runBlocking {
        val viewModel = ShellConfigViewModel(
            FakeFeatureFlagService { _, _ ->
                flowOf(
                    setOf(FeatureFlagKeys.MODULE_INVENTORY),
                    setOf(FeatureFlagKeys.MODULE_INVENTORY, FeatureFlagKeys.MODULE_SYNC),
                )
            },
        )

        assertEquals(
            listOf(
                setOf(FeatureFlagKeys.MODULE_INVENTORY),
                setOf(FeatureFlagKeys.MODULE_INVENTORY, FeatureFlagKeys.MODULE_SYNC),
            ),
            viewModel.enabledModuleKeys(context("tenant-a", "business-a")).toList(),
        )
    }

    @Test
    fun resolutionFailureFallsBackToNoGating() = runBlocking {
        val viewModel = ShellConfigViewModel(
            FakeFeatureFlagService { _, _ ->
                flow<Set<String>> {
                    throw IllegalStateException("business not available to configuration")
                }
            },
        )

        assertNull(viewModel.enabledModuleKeys(context("tenant-a", "business-a")).first())
    }

    @Test
    fun resolvedSetDrivesRegistryVisibility() = runBlocking {
        val viewModel = ShellConfigViewModel(
            FakeFeatureFlagService { _, _ ->
                flowOf(setOf(FeatureFlagKeys.MODULE_INVENTORY, FeatureFlagKeys.MODULE_ACCOUNTING))
            },
        )

        val keys = viewModel.enabledModuleKeys(context("tenant-a", "business-a")).first()

        // Sales (orders), purchases and cloud sync (sync) are gated out;
        // items and parties declare no config key and stay visible.
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.ACCOUNTS_ID,
            ),
            AppModuleRegistry.visibleModules(keys).map { it.id },
        )
    }
}
