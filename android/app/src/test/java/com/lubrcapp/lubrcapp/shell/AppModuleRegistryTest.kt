package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry contract for the Phase 13 app shell: the registry is the single
 * source the shell reads, so its contents, order, and gating metadata must
 * stay exact. The expected order is the pre-registry shell order, which
 * the registry refactor must not change.
 */
class AppModuleRegistryTest {

    @Test
    fun registryContainsEveryShellModuleInShellOrder() {
        assertEquals(
            listOf(
                AppModuleRegistry.DASHBOARD_ID,
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.SALES_ID,
                AppModuleRegistry.PURCHASES_ID,
                AppModuleRegistry.ACCOUNTS_ID,
                AppModuleRegistry.CLOUD_SYNC_ID,
            ),
            AppModuleRegistry.modules.map { it.id },
        )
    }

    @Test
    fun moduleIdsAreUnique() {
        val ids = AppModuleRegistry.modules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
    }

    @Test
    fun everyModuleHasATitleAndSelectableModulesHaveContent() {
        AppModuleRegistry.modules.forEach { module ->
            assertTrue(
                "module ${module.id} must declare a title resource",
                module.titleRes != 0,
            )
        }
        AppModuleRegistry.navigationModules().forEach { module ->
            assertNotNull(
                "selectable module ${module.id} must render content",
                module.content,
            )
        }
    }

    @Test
    fun dashboardIsRegisteredAsHomeAndNotSelectable() {
        val dashboard = AppModuleRegistry.module(AppModuleRegistry.DASHBOARD_ID)
        assertNotNull(dashboard)
        assertEquals(false, dashboard!!.selectable)
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.SALES_ID,
                AppModuleRegistry.PURCHASES_ID,
                AppModuleRegistry.ACCOUNTS_ID,
                AppModuleRegistry.CLOUD_SYNC_ID,
            ),
            AppModuleRegistry.navigationModules().map { it.id },
        )
    }

    @Test
    fun unknownAndNullIdsResolveToNoModule() {
        assertNull(AppModuleRegistry.module(null))
        assertNull(AppModuleRegistry.module(""))
        assertNull(AppModuleRegistry.module("reports"))
    }

    @Test
    fun defaultVisibilityShowsEverySelectableModuleInOrder() {
        assertEquals(
            AppModuleRegistry.navigationModules().map { it.id },
            AppModuleRegistry.visibleModules().map { it.id },
        )
    }

    @Test
    fun configVisibilityHidesOnlyGatedModulesWhoseKeyIsAbsent() {
        // Only inventory is enabled by configuration: gated modules whose
        // key is missing are hidden, while items and parties declare no
        // config key and stay visible.
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
            ),
            AppModuleRegistry.visibleModules(setOf(FeatureFlagKeys.MODULE_INVENTORY))
                .map { it.id },
        )
        assertEquals(
            listOf(AppModuleRegistry.ITEMS_ID, AppModuleRegistry.PARTIES_ID),
            AppModuleRegistry.visibleModules(emptySet()).map { it.id },
        )
        assertEquals(
            AppModuleRegistry.navigationModules().map { it.id },
            AppModuleRegistry.visibleModules(FeatureFlagKeys.ALL).map { it.id },
        )
    }

    @Test
    fun declaredConfigKeysExistInTheFeatureFlagKeySpace() {
        val declared = AppModuleRegistry.modules.mapNotNull { it.configKey }
        assertTrue(declared.isNotEmpty())
        assertTrue(
            "registry config keys must come from FeatureFlagKeys: $declared",
            FeatureFlagKeys.ALL.containsAll(declared),
        )
    }

    @Test
    fun declaredCapabilitiesAreDefinedByTheAccessControlCatalog() {
        val defined = Capabilities.DEFINITIONS.map { it.key }.toSet()
        AppModuleRegistry.modules.forEach { module ->
            val capability = module.viewCapability ?: return@forEach
            assertTrue(
                "module ${module.id} declares undefined capability $capability",
                capability in defined,
            )
        }
        assertEquals(
            Capabilities.SALES_VIEW,
            AppModuleRegistry.module(AppModuleRegistry.SALES_ID)?.viewCapability,
        )
        assertEquals(
            Capabilities.SYNC_MANAGE,
            AppModuleRegistry.module(AppModuleRegistry.CLOUD_SYNC_ID)?.viewCapability,
        )
    }
}
