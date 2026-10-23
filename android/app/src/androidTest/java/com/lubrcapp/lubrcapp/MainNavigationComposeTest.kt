package com.lubrcapp.lubrcapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.designsystem.BrandPalette
import com.lubrcapp.lubrcapp.core.designsystem.DesignSystemConfig
import com.lubrcapp.lubrcapp.core.designsystem.LubrcappTheme
import com.lubrcapp.lubrcapp.core.repository.AccessControlRepository
import com.lubrcapp.lubrcapp.core.repository.AuthorizationSnapshot
import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.BusinessChoice
import com.lubrcapp.lubrcapp.core.repository.BusinessMember
import com.lubrcapp.lubrcapp.core.repository.BusinessRole
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitcher
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessRoleRequest
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessUserRequest
import com.lubrcapp.lubrcapp.core.repository.CreateInitialBusinessRequest
import com.lubrcapp.lubrcapp.core.repository.CurrentBusinessProvider
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.repository.usecase.CreateInitialBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.EnsureCurrentBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveAvailableBusinessesUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveBusinessBootstrapUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveCurrentBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.SwitchBusinessUseCase
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.feature.auth.BusinessSetupViewModel
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardViewModel
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagService
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-level navigation routing for the state-based MainNavigation (12B-09):
 * the bootstrap state decides the top-level surface — progress while
 * loading, onboarding when setup is needed, an error surface when the
 * local state cannot be read, and the dashboard with feature navigation
 * when ready. Phase 13.4 shapes the ready surface as a Scaffold: the
 * registry-driven bottom navigation (Home / Items / Customers / More)
 * with the full module list under More. ViewModels are built on fake
 * repositories, mirroring the feature-level test fakes; feature screens
 * themselves keep their own back-navigation tests in their modules, so
 * these tests pin the bar and the More/list surfaces — click-through
 * into a feature surface still needs the Hilt-graph harness (see 12B-09).
 */
@RunWith(AndroidJUnit4::class)
class MainNavigationComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class FakeSetupRepository(
        private val bootstrapFlow: Flow<BusinessBootstrap?>,
    ) : BusinessSetupRepository {
        override fun observeBootstrap(): Flow<BusinessBootstrap?> = bootstrapFlow
        override suspend fun getBootstrap(): BusinessBootstrap? = null
        override suspend fun createInitialBusiness(
            request: CreateInitialBusinessRequest,
        ): BusinessBootstrap = error("not used in navigation tests")
    }

    private class FakeBusinessContext : CurrentBusinessProvider, BusinessSwitcher {
        val userId = UserId("owner-1")
        val alpha = BusinessChoice(
            context = TenantContext(TenantId("tenant-a"), BusinessId("business-a"), userId),
            name = "Alpha",
            businessType = "Retail",
            currency = CurrencyCode("USD"),
            timezone = ZoneId.of("UTC"),
            languageCode = "en",
        )
        val current = MutableStateFlow<BusinessChoice?>(null)
        val available = MutableStateFlow<List<BusinessChoice>>(emptyList())

        override fun observeCurrent(userId: UserId): Flow<BusinessChoice?> = current
        override suspend fun current(userId: UserId): BusinessChoice? = current.value
        override suspend fun ensureCurrent(
            userId: UserId,
            fallback: TenantContext?,
        ): BusinessChoice? {
            current.value?.let { return it }
            val chosen = fallback?.let { wanted ->
                available.value.firstOrNull { it.context == wanted }
            } ?: available.value.firstOrNull()
            current.value = chosen
            return chosen
        }
        override fun observeAvailableBusinesses(
            userId: UserId,
        ): Flow<List<BusinessChoice>> = available
        override suspend fun switchBusiness(
            userId: UserId,
            tenantId: TenantId,
            businessId: BusinessId,
        ): BusinessSwitchResult {
            val chosen = available.value.firstOrNull {
                it.context.tenantId == tenantId && it.context.businessId == businessId
            } ?: return BusinessSwitchResult.AccessDenied(tenantId, businessId)
            current.value = chosen
            return BusinessSwitchResult.Switched(chosen)
        }
    }

    private class FakeDeviceIdentity : DeviceIdentityProvider {
        override suspend fun getOrCreate(): DeviceId = DeviceId("device-a")
    }

    /** The gated module keys the shipped deployment configuration enables. */
    private val shippedEnabledKeys = setOf(
        FeatureFlagKeys.MODULE_INVENTORY,
        FeatureFlagKeys.MODULE_ORDERS,
        FeatureFlagKeys.MODULE_PURCHASES,
        FeatureFlagKeys.MODULE_ACCOUNTING,
        FeatureFlagKeys.MODULE_SYNC,
    )

    private class FakeFeatureFlags(
        private val enabled: Flow<Set<String>>,
    ) : FeatureFlagService {
        override fun observeEnabledFeatures(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Flow<Set<String>> = enabled

        override suspend fun isEnabled(
            tenantId: TenantId,
            businessId: BusinessId,
            feature: String,
        ): Boolean = enabled.first().contains(feature)

        override fun getAllFeatureKeys(): Set<String> = FeatureFlagKeys.ALL
    }

    /**
     * The current user is an active member of the context's business;
     * [grants] decides which capabilities the snapshot carries. The
     * default in [showNavigation] grants everything, mirroring the
     * owner's seeded role.
     */
    private class FakeAccessControl(
        private val grants: Flow<Set<CapabilityKey>>,
    ) : AccessControlRepository {
        override fun observeAuthorization(
            context: TenantContext,
        ): Flow<AuthorizationSnapshot> =
            grants.map { granted ->
                AuthorizationSnapshot(
                    context = context,
                    membershipId = UserBusinessId("membership-1"),
                    roleIds = setOf(RoleId("role-owner")),
                    capabilities = granted,
                )
            }

        override suspend fun authorization(
            context: TenantContext,
        ): AuthorizationSnapshot = observeAuthorization(context).first()

        override suspend fun ensureOwnerAccess(
            tenantId: TenantId,
            businessId: BusinessId,
        ) = Unit

        override fun observeMembers(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Flow<List<BusinessMember>> = flowOf(emptyList())

        override fun observeRoles(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Flow<List<BusinessRole>> = flowOf(emptyList())

        override suspend fun createBusinessUser(
            actor: TenantContext,
            request: CreateBusinessUserRequest,
        ): BusinessMember = error("not used in navigation tests")

        override suspend fun setMembershipActive(
            actor: TenantContext,
            membershipId: UserBusinessId,
            active: Boolean,
        ): Unit = error("not used in navigation tests")

        override suspend fun createRole(
            actor: TenantContext,
            request: CreateBusinessRoleRequest,
        ): BusinessRole = error("not used in navigation tests")

        override suspend fun setRoleActive(
            actor: TenantContext,
            roleId: RoleId,
            active: Boolean,
        ): Unit = error("not used in navigation tests")

        override suspend fun setRoleCapabilities(
            actor: TenantContext,
            roleId: RoleId,
            capabilities: Set<CapabilityKey>,
        ): Unit = error("not used in navigation tests")

        override suspend fun assignRoles(
            actor: TenantContext,
            membershipId: UserBusinessId,
            roleIds: Set<RoleId>,
        ): Unit = error("not used in navigation tests")
    }

    private fun setupViewModel(flow: Flow<BusinessBootstrap?>): BusinessSetupViewModel {
        val repository = FakeSetupRepository(flow)
        return BusinessSetupViewModel(
            observeBootstrap = ObserveBusinessBootstrapUseCase(repository),
            createInitialBusiness = CreateInitialBusinessUseCase(repository),
        )
    }

    private fun dashboardViewModel(context: FakeBusinessContext): DashboardViewModel =
        DashboardViewModel(
            observeCurrentBusiness = ObserveCurrentBusinessUseCase(context),
            ensureCurrentBusiness = EnsureCurrentBusinessUseCase(context),
            observeAvailableBusinesses = ObserveAvailableBusinessesUseCase(context),
            switchBusiness = SwitchBusinessUseCase(context),
        )

    private fun showNavigation(
        setup: BusinessSetupViewModel,
        context: FakeBusinessContext = FakeBusinessContext(),
        enabledKeys: Flow<Set<String>> = flowOf(shippedEnabledKeys),
        grants: Flow<Set<CapabilityKey>> = flowOf(Capabilities.ALL),
    ) {
        // Construct view models outside the composable lambda: lint treats a
        // direct ViewModel constructor call inside setContent as an error
        // (ViewModelConstructorInComposable).
        val dashboard = dashboardViewModel(context)
        val scope = OperationalScopeViewModel(FakeDeviceIdentity())
        val shellConfig = ShellConfigViewModel(FakeFeatureFlags(enabledKeys))
        val shellAccess = ShellAccessViewModel(FakeAccessControl(grants))
        compose.setContent {
            // MainActivity hosts MainNavigation inside LubrcappTheme, which
            // provides LocalDesignTokens; without it any screen reading
            // LubrcTheme.tokens crashes. Mirror the production host here.
            LubrcappTheme(config = testDesignSystemConfig) {
                MainNavigation(
                    appName = "Lubric",
                    businessSetupViewModel = setup,
                    dashboardViewModel = dashboard,
                    scopeViewModel = scope,
                    shellConfigViewModel = shellConfig,
                    shellAccessViewModel = shellAccess,
                )
            }
        }
    }

    private companion object {
        /** Neutral design-system config for tests (mirrors ThemeModelsTest). */
        val testDesignSystemConfig = DesignSystemConfig(
            palette = BrandPalette(
                primary = 0xFF112233.toInt(),
                secondary = 0xFF223344.toInt(),
                accent = 0xFF334455.toInt(),
                success = 0xFF006600.toInt(),
                warning = 0xFFFFAA00.toInt(),
                error = 0xFFAA0000.toInt(),
            ),
        )
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5000) {
            try {
                compose.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun awaitNoText(text: String) {
        compose.waitUntil(5000) {
            try {
                compose.onNodeWithText(text).assertDoesNotExist()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun readyBootstrap() = BusinessBootstrap(
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        ownerUserId = UserId("owner-1"),
        businessName = "Alpha",
        languageCode = "en",
    )

    @Test fun loadingBootstrapShowsProgress() {
        showNavigation(setupViewModel(flow { }))
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        compose.onNodeWithText("English").assertDoesNotExist()
    }

    @Test fun missingBootstrapShowsOnboarding() {
        showNavigation(setupViewModel(flowOf(null)))
        awaitText("English")
        compose.onNodeWithText("Items").assertDoesNotExist()
    }

    @Test fun failingBootstrapShowsErrorSurface() {
        showNavigation(setupViewModel(flow { throw IllegalStateException("storage gone") }))
        awaitText("Unable to load the local business state.")
    }

    @Test fun readyBootstrapShowsBottomNavWithHomeItemsCustomersMore() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context)
        awaitText("Home")
        listOf("Home", "Items", "Customers", "More")
            .forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
        // The module list lives under More now, not on the ready surface.
        compose.onNodeWithText("Inventory").assertDoesNotExist()
    }

    @Test fun moreSurfaceShowsFullModuleListAndHomeReturns() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context)
        awaitText("Home")
        compose.onNodeWithText("More").performClick()
        awaitText("Inventory")
        // Items/Customers hold bottom-bar slots too, so in the More state
        // their labels exist twice (bar + list) — assert only the
        // non-slotted modules, which are unique to the list.
        listOf("Inventory", "Sales", "Purchases", "Accounts", "Cloud sync")
            .forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
        compose.onNodeWithText("Home").performClick()
        awaitNoText("Inventory")
    }

    @Test fun readyBootstrapHidesModulesExcludedByConfiguration() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        // The resolved configuration for this business enables inventory,
        // orders (sales) and accounting only: purchases and cloud sync
        // are gated out, while items and customers have no config key
        // and always stay.
        val gated = flowOf(
            setOf(
                FeatureFlagKeys.MODULE_INVENTORY,
                FeatureFlagKeys.MODULE_ORDERS,
                FeatureFlagKeys.MODULE_ACCOUNTING,
            ),
        )
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context, gated)
        awaitText("Items")
        compose.onNodeWithText("More").performClick()
        awaitText("Inventory")
        listOf("Inventory", "Sales", "Accounts")
            .forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
        awaitNoText("Purchases")
        awaitNoText("Cloud sync")
    }

    @Test fun readyBootstrapHidesModulesDeniedByCapability() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        // The configuration enables everything (the harness default),
        // but the current user's grants exclude purchases.view: only the
        // Purchases button leaves the shell list.
        val grants = flowOf(Capabilities.ALL - Capabilities.PURCHASES_VIEW)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context, grants = grants)
        awaitText("Items")
        compose.onNodeWithText("More").performClick()
        awaitText("Inventory")
        listOf("Inventory", "Sales", "Accounts", "Cloud sync")
            .forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
        awaitNoText("Purchases")
    }

    @Test fun bottomNavOmitsSlottedModuleDeniedByCapability() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        // Parties (Customers) holds a bottom-bar slot and declares a view
        // capability: denying parties.view removes it from the bar, while
        // Home, Items and More stay.
        val grants = flowOf(Capabilities.ALL - Capabilities.PARTIES_VIEW)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context, grants = grants)
        awaitText("Home")
        compose.onNodeWithText("Customers").assertDoesNotExist()
        listOf("Home", "Items", "More")
            .forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
    }

    @Test fun capabilityRevocationHidesModuleLive() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        // Grants change under the running shell (a role edit in Access
        // Control, a sync applying new permissions): the module list
        // follows without a restart or a business switch.
        val grants = MutableStateFlow(Capabilities.ALL)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context, grants = grants)
        awaitText("Home")
        compose.onNodeWithText("More").performClick()
        awaitText("Purchases")
        grants.value = Capabilities.ALL - Capabilities.PURCHASES_VIEW
        awaitNoText("Purchases")
    }

    @Test fun quickActionsSectionAndFabSheetListVisibleActions() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context)
        awaitText("Home")
        // The dashboard's quick-actions section lists each action once.
        compose.onAllNodesWithText("+ Sale").assertCountEquals(1)
        // The FAB (content description "Quick actions") opens the sheet,
        // which lists every visible action a second time.
        compose.onNodeWithContentDescription("Quick actions").performClick()
        compose.waitUntil(5000) {
            try {
                compose.onAllNodesWithText("+ Sale").assertCountEquals(2)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        listOf("+ Purchase", "+ Order", "Party", "Item", "Stock Adjustment", "Expense", "Income")
            .forEach { label -> compose.onAllNodesWithText(label).assertCountEquals(2) }
    }

    @Test fun quickActionsHideCapabilitiesTheUserLacks() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        // No sales.create: + Sale and + Order appear neither in the
        // dashboard section nor in the FAB sheet, while the granted
        // actions still list in both surfaces.
        val grants = flowOf(Capabilities.ALL - Capabilities.SALES_CREATE)
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context, grants = grants)
        awaitText("Home")
        compose.onNodeWithContentDescription("Quick actions").performClick()
        compose.waitUntil(5000) {
            try {
                compose.onAllNodesWithText("+ Purchase").assertCountEquals(2)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onAllNodesWithText("+ Sale").assertCountEquals(0)
        compose.onAllNodesWithText("+ Order").assertCountEquals(0)
    }

    @Test fun fabVanishesWhenNoQuickActionIsVisible() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        // A member with no capabilities at all: no slotted module passes
        // its view gate and no action passes its capability gate, so the
        // FAB is not rendered (no dead buttons) and no action shows.
        val grants = flowOf(emptySet<CapabilityKey>())
        showNavigation(setupViewModel(flowOf(readyBootstrap())), context, grants = grants)
        awaitText("Home")
        compose.onNodeWithContentDescription("Quick actions").assertDoesNotExist()
        compose.onAllNodesWithText("+ Sale").assertCountEquals(0)
    }
}
