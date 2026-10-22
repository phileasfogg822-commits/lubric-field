package com.lubrcapp.lubrcapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.BusinessChoice
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitcher
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
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.feature.auth.BusinessSetupViewModel
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardViewModel
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-level navigation routing for the state-based MainNavigation (12B-09):
 * the bootstrap state decides the top-level surface — progress while
 * loading, onboarding when setup is needed, an error surface when the
 * local state cannot be read, and the dashboard with feature navigation
 * when ready. ViewModels are built on fake repositories, mirroring the
 * feature-level test fakes; feature screens themselves keep their own
 * back-navigation tests in their modules.
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
    ) {
        compose.setContent {
            MainNavigation(
                appName = "Lubric",
                businessSetupViewModel = setup,
                dashboardViewModel = dashboardViewModel(context),
                scopeViewModel = OperationalScopeViewModel(FakeDeviceIdentity()),
            )
        }
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

    @Test fun readyBootstrapShowsDashboardWithFeatureNavigation() {
        val context = FakeBusinessContext()
        context.available.value = listOf(context.alpha)
        val bootstrap = BusinessBootstrap(
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-a"),
            ownerUserId = UserId("owner-1"),
            businessName = "Alpha",
            languageCode = "en",
        )
        showNavigation(setupViewModel(flowOf(bootstrap)), context)
        awaitText("Items")
        listOf("Inventory", "Customers", "Sales", "Purchases", "Accounts", "Cloud sync")
            .forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
    }
}
