package com.lubrcapp.lubrcapp.core.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun availability(): BiometricAvailability {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return when (
            BiometricManager.from(context).canAuthenticate(authenticators)
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.TEMPORARILY_UNAVAILABLE
            else -> BiometricAvailability.UNSUPPORTED
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (BiometricResult) -> Unit,
    ) {
        if (availability() != BiometricAvailability.AVAILABLE) {
            onResult(BiometricResult.Failure("Biometric authentication is unavailable"))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onResult(BiometricResult.Success)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onResult(BiometricResult.Cancelled)
                    } else {
                        onResult(BiometricResult.Failure(errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    onResult(BiometricResult.Failure("Biometric authentication failed"))
                }
            },
        )

        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")

        subtitle?.takeIf(String::isNotBlank)?.let(infoBuilder::setSubtitle)
        prompt.authenticate(infoBuilder.build())
    }
}
