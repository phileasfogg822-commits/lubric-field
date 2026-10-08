package com.lubrcapp.lubrcapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lubrcapp.lubrcapp.core.auth.AuthRemoteResult
import com.lubrcapp.lubrcapp.core.auth.AuthSessionController
import com.lubrcapp.lubrcapp.core.auth.AuthState
import com.lubrcapp.lubrcapp.core.auth.OtpChallenge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionController: AuthSessionController,
) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionController.state

    private val mutableOnboardingMode = MutableStateFlow<AuthOnboardingMode?>(null)
    val onboardingMode: StateFlow<AuthOnboardingMode?> = mutableOnboardingMode.asStateFlow()

    private val mutableOtpChallenge = MutableStateFlow<OtpChallenge?>(null)
    val otpChallenge: StateFlow<OtpChallenge?> = mutableOtpChallenge.asStateFlow()

    private val mutableOtpError = MutableStateFlow<String?>(null)
    val otpError: StateFlow<String?> = mutableOtpError.asStateFlow()

    init {
        viewModelScope.launch {
            sessionController.initialize()
        }
    }

    fun showOnboarding(mode: AuthOnboardingMode) {
        mutableOnboardingMode.value = mode
    }

    fun clearOnboardingMode() {
        mutableOnboardingMode.value = null
    }

    fun signInWithEmail(
        email: String,
        password: CharArray,
    ) {
        viewModelScope.launch {
            sessionController.signInWithEmail(email, password)
        }
    }

    fun requestOtp(phone: String) {
        mutableOtpError.value = null
        viewModelScope.launch {
            when (val result = sessionController.requestOtp(phone)) {
                is AuthRemoteResult.Success -> {
                    mutableOtpChallenge.value = result.value
                }
                is AuthRemoteResult.InvalidCredentials -> {
                    mutableOtpError.value = result.message
                }
                is AuthRemoteResult.Unavailable -> {
                    mutableOtpError.value = result.reason
                }
                is AuthRemoteResult.Failure -> {
                    mutableOtpError.value = result.message
                }
            }
        }
    }

    fun verifyOtp(otp: CharArray) {
        val challenge = mutableOtpChallenge.value ?: return
        viewModelScope.launch {
            sessionController.verifyOtp(challenge.challengeId, otp)
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            sessionController.refresh()
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionController.logout()
        }
    }

    fun lock() {
        viewModelScope.launch {
            sessionController.lock()
        }
    }

    fun unlockWithPin(pin: CharArray) {
        viewModelScope.launch {
            sessionController.unlockWithPin(pin)
        }
    }

    fun setPin(pin: CharArray) {
        viewModelScope.launch {
            sessionController.setPin(pin)
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            sessionController.clearPin()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionController.setBiometricEnabled(enabled)
        }
    }

    fun confirmBiometricSuccess() {
        viewModelScope.launch {
            sessionController.unlockAfterBiometricSuccess()
        }
    }
}
