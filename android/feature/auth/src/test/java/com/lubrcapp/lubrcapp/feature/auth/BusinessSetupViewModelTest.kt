package com.lubrcapp.lubrcapp.feature.auth

import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import com.lubrcapp.lubrcapp.core.repository.CreateInitialBusinessRequest
import com.lubrcapp.lubrcapp.core.repository.usecase.CreateInitialBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveBusinessBootstrapUseCase
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BusinessSetupViewModelTest {
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
    fun firstRunStartsFromLocalBootstrapAndValidatesRequiredSteps() = runTest(dispatcher) {
        val repository = FakeBusinessSetupRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(BusinessBootstrapState.NeedsSetup, viewModel.state.value.bootstrapState)
        assertEquals(BusinessSetupStep.LANGUAGE, viewModel.state.value.step)

        viewModel.next()
        assertEquals(BusinessSetupStep.WELCOME, viewModel.state.value.step)
        viewModel.next()
        assertEquals(BusinessSetupStep.ENTRY, viewModel.state.value.step)
        viewModel.next()
        assertEquals(BusinessSetupStep.BUSINESS_DETAILS, viewModel.state.value.step)

        viewModel.next()
        assertEquals(BusinessSetupStep.BUSINESS_DETAILS, viewModel.state.value.step)
        assertEquals(BusinessSetupError.BUSINESS_NAME_REQUIRED, viewModel.state.value.error)
    }

    @Test
    fun completingCreateFlowPublishesReadyBootstrap() = runTest(dispatcher) {
        val repository = FakeBusinessSetupRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.selectLanguage("ar")
        viewModel.next()
        viewModel.next()
        viewModel.next()

        viewModel.updateBusinessName("Offline Business")
        viewModel.next()

        val country = viewModel.countries.first()
        viewModel.selectCountry(country.code)
        viewModel.next()

        if (viewModel.state.value.draft.currencyCode.isBlank()) {
            viewModel.selectCurrency(viewModel.currencies.first().code)
        }
        viewModel.next()

        viewModel.updateTaxEnabled(true)
        viewModel.updateTaxNumber("TAX-1")
        viewModel.next()

        viewModel.updateBusinessType("General Trading")
        viewModel.next()

        viewModel.updateOwnerName("Owner")
        viewModel.updateOwnerEmail("owner@example.test")
        viewModel.next()

        assertEquals(BusinessSetupStep.REVIEW, viewModel.state.value.step)

        viewModel.createBusiness()
        advanceUntilIdle()

        assertNotNull(repository.lastRequest)
        assertEquals("ar", repository.lastRequest?.languageCode)
        assertEquals("Offline Business", repository.lastRequest?.businessName)
        assertEquals("General Trading", repository.lastRequest?.businessType)
        assertTrue(repository.lastRequest?.taxEnabled == true)

        val ready = viewModel.state.value.bootstrapState
        assertTrue(ready is BusinessBootstrapState.Ready)
        assertEquals("Offline Business", (ready as BusinessBootstrapState.Ready).bootstrap.businessName)
    }

    private fun viewModel(
        repository: BusinessSetupRepository,
    ) = BusinessSetupViewModel(
        observeBootstrap = ObserveBusinessBootstrapUseCase(repository),
        createInitialBusiness = CreateInitialBusinessUseCase(repository),
    )

    private class FakeBusinessSetupRepository : BusinessSetupRepository {
        private val bootstrap = MutableStateFlow<BusinessBootstrap?>(null)
        var lastRequest: CreateInitialBusinessRequest? = null

        override fun observeBootstrap(): Flow<BusinessBootstrap?> = bootstrap

        override suspend fun getBootstrap(): BusinessBootstrap? = bootstrap.value

        override suspend fun createInitialBusiness(
            request: CreateInitialBusinessRequest,
        ): BusinessBootstrap {
            lastRequest = request
            return BusinessBootstrap(
                tenantId = TenantId("tenant-1"),
                businessId = BusinessId("business-1"),
                ownerUserId = UserId("owner-1"),
                businessName = request.businessName,
                languageCode = request.languageCode,
            ).also { bootstrap.value = it }
        }
    }
}
