package com.lubrcapp.lubrcapp.feature.syncstatus

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.CloudPairingGateway
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CloudSyncUiState(
    val paired: Boolean = false,
    val endpoint: String? = null,
    val error: String? = null,
)

/**
 * Cloud sync pairing state for one operational scope. All work is
 * suspend-based so the screen owns the coroutine, mirroring the other
 * operational feature ViewModels.
 */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val gateway: CloudPairingGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(CloudSyncUiState())
    val state: StateFlow<CloudSyncUiState> = _state.asStateFlow()

    suspend fun refresh(scope: OperationalScope) {
        _state.value = CloudSyncUiState(
            paired = gateway.isPaired(scope),
            endpoint = gateway.pairedEndpoint(scope),
        )
    }

    suspend fun pair(scope: OperationalScope, endpoint: String, token: String) {
        try {
            gateway.pair(scope, endpoint.trim(), token)
            refresh(scope)
        } catch (failure: Exception) {
            _state.value = _state.value.copy(
                error = failure.message ?: "Pairing failed",
            )
        }
    }

    suspend fun unpair(scope: OperationalScope) {
        try {
            gateway.unpair(scope)
            refresh(scope)
        } catch (failure: Exception) {
            _state.value = _state.value.copy(
                error = failure.message ?: "Unpairing failed",
            )
        }
    }
}
