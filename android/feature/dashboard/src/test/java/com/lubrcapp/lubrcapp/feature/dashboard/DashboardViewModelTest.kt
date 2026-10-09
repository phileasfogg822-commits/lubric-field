package com.lubrcapp.lubrcapp.feature.dashboard

import com.lubrcapp.lubrcapp.core.repository.BusinessChoice
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitcher
import com.lubrcapp.lubrcapp.core.repository.CurrentBusinessProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.repository.usecase.EnsureCurrentBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveAvailableBusinessesUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveCurrentBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.SwitchBusinessUseCase
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startRestoresValidatedFallbackAndObservesAvailableBusinesses() = runTest(dispatcher) {
        val fake = FakeBusinessContext()
        fake.available.value = listOf(fake.alpha, fake.beta)
        val viewModel = viewModel(fake)

        viewModel.start(
            userId = fake.userId,
            fallbackTenantId = fake.alpha.context.tenantId,
            fallbackBusinessId = fake.alpha.context.businessId,
        )
        advanceUntilIdle()

        assertEquals("business-a", viewModel.state.value.currentBusiness?.context?.businessId?.value)
        assertEquals(2, viewModel.state.value.availableBusinesses.size)
        assertTrue(!viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun switchMovesOnlyToBusinessReturnedBySwitcher() = runTest(dispatcher) {
        val fake = FakeBusinessContext()
        fake.available.value = listOf(fake.alpha, fake.beta)
        val viewModel = viewModel(fake)

        viewModel.start(
            userId = fake.userId,
            fallbackTenantId = fake.alpha.context.tenantId,
            fallbackBusinessId = fake.alpha.context.businessId,
        )
        advanceUntilIdle()

        viewModel.switchTo(
            fake.beta.context.tenantId,
            fake.beta.context.businessId,
        )
        advanceUntilIdle()

        assertEquals("business-b", viewModel.state.value.currentBusiness?.context?.businessId?.value)
        assertEquals("ar", viewModel.state.value.currentBusiness?.languageCode)
    }

    @Test
    fun revokedCurrentBusinessRecoversToRemainingAccessibleBusiness() = runTest(dispatcher) {
        val fake = FakeBusinessContext()
        fake.available.value = listOf(fake.alpha, fake.beta)
        fake.current.value = fake.beta
        val viewModel = viewModel(fake)

        viewModel.start(
            userId = fake.userId,
            fallbackTenantId = fake.alpha.context.tenantId,
            fallbackBusinessId = fake.alpha.context.businessId,
        )
        advanceUntilIdle()

        assertEquals("business-b", viewModel.state.value.currentBusiness?.context?.businessId?.value)

        fake.available.value = listOf(fake.alpha)
        fake.current.value = null
        advanceUntilIdle()

        assertEquals("business-a", viewModel.state.value.currentBusiness?.context?.businessId?.value)
        assertNull(viewModel.state.value.errorMessage)
    }

    private fun viewModel(
        fake: FakeBusinessContext,
    ) = DashboardViewModel(
        observeCurrentBusiness = ObserveCurrentBusinessUseCase(fake),
        ensureCurrentBusiness = EnsureCurrentBusinessUseCase(fake),
        observeAvailableBusinesses = ObserveAvailableBusinessesUseCase(fake),
        switchBusiness = SwitchBusinessUseCase(fake),
    )

    private class FakeBusinessContext : CurrentBusinessProvider, BusinessSwitcher {
        val userId = UserId("user-1")
        val alpha = choice("tenant-a", "business-a", "Alpha", "USD", "en")
        val beta = choice("tenant-b", "business-b", "Beta", "AED", "ar")

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
                it.context.userId == userId &&
                    it.context.tenantId == tenantId &&
                    it.context.businessId == businessId
            } ?: return BusinessSwitchResult.AccessDenied(tenantId, businessId)

            current.value = chosen
            return BusinessSwitchResult.Switched(chosen)
        }

        private fun choice(
            tenant: String,
            business: String,
            name: String,
            currency: String,
            language: String,
        ) = BusinessChoice(
            context = TenantContext(
                tenantId = TenantId(tenant),
                businessId = BusinessId(business),
                userId = userId,
            ),
            name = name,
            businessType = "General",
            currency = CurrencyCode.of(currency),
            timezone = ZoneId.of("UTC"),
            languageCode = language,
        )
    }
}
