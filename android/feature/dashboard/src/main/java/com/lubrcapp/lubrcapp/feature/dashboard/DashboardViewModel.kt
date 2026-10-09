package com.lubrcapp.lubrcapp.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lubrcapp.lubrcapp.core.repository.BusinessChoice
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.repository.usecase.EnsureCurrentBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveAvailableBusinessesUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveCurrentBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.SwitchBusinessUseCase
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DashboardUiState(
    val currentBusiness: BusinessChoice? = null,
    val availableBusinesses: List<BusinessChoice> = emptyList(),
    val isLoading: Boolean = true,
    val isSwitching: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeCurrentBusiness: ObserveCurrentBusinessUseCase,
    private val ensureCurrentBusiness: EnsureCurrentBusinessUseCase,
    private val observeAvailableBusinesses: ObserveAvailableBusinessesUseCase,
    private val switchBusiness: SwitchBusinessUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()

    private var activeUserId: UserId? = null
    private var observationJob: Job? = null

    fun start(
        userId: UserId,
        fallbackTenantId: TenantId,
        fallbackBusinessId: BusinessId,
    ) {
        if (activeUserId == userId && observationJob?.isActive == true) return

        activeUserId = userId
        observationJob?.cancel()
        mutableState.value = DashboardUiState(isLoading = true)

        val fallback = TenantContext(
            tenantId = fallbackTenantId,
            businessId = fallbackBusinessId,
            userId = userId,
        )

        observationJob = viewModelScope.launch {
            runCatching {
                ensureCurrentBusiness(userId, fallback)
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unable to restore business context",
                )
            }

            combine(
                observeCurrentBusiness(userId),
                observeAvailableBusinesses(userId),
            ) { current, available ->
                current to available
            }.collect { (current, available) ->
                mutableState.value = mutableState.value.copy(
                    currentBusiness = current,
                    availableBusinesses = available,
                    isLoading = false,
                    isSwitching = false,
                    errorMessage = if (current == null && available.isNotEmpty()) {
                        "No valid current business is selected"
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun switchTo(
        tenantId: TenantId,
        businessId: BusinessId,
    ) {
        val userId = activeUserId ?: return
        if (mutableState.value.isSwitching) return

        mutableState.value = mutableState.value.copy(
            isSwitching = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            when (val result = switchBusiness(userId, tenantId, businessId)) {
                is BusinessSwitchResult.Switched -> {
                    mutableState.value = mutableState.value.copy(
                        currentBusiness = result.business,
                        isSwitching = false,
                        errorMessage = null,
                    )
                }
                is BusinessSwitchResult.AccessDenied -> {
                    mutableState.value = mutableState.value.copy(
                        isSwitching = false,
                        errorMessage = "You do not have access to that business",
                    )
                }
                is BusinessSwitchResult.Unavailable -> {
                    mutableState.value = mutableState.value.copy(
                        isSwitching = false,
                        errorMessage = "That business is no longer available",
                    )
                }
            }
        }
    }
}
