package com.lubrcapp.lubrcapp.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DeviceSecurityPolicy(
    val blockScreenshots: Boolean = true,
    val lockOnBackground: Boolean = true,
)

interface SecurityPolicyStore {
    val policy: StateFlow<DeviceSecurityPolicy>

    suspend fun initialize()
    suspend fun setBlockScreenshots(enabled: Boolean)
    suspend fun setLockOnBackground(enabled: Boolean)
}

@Singleton
class SecureSecurityPolicyStore @Inject constructor(
    private val storage: SecureAuthStorage,
) : SecurityPolicyStore {
    private val mutablePolicy = MutableStateFlow(DeviceSecurityPolicy())
    override val policy: StateFlow<DeviceSecurityPolicy> = mutablePolicy.asStateFlow()

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        mutablePolicy.value = DeviceSecurityPolicy(
            blockScreenshots = storage.read(KEY_BLOCK_SCREENSHOTS)
                ?.toBooleanStrictOrNull()
                ?: true,
            lockOnBackground = storage.read(KEY_LOCK_ON_BACKGROUND)
                ?.toBooleanStrictOrNull()
                ?: true,
        )
    }

    override suspend fun setBlockScreenshots(enabled: Boolean) = withContext(Dispatchers.IO) {
        storage.write(mapOf(KEY_BLOCK_SCREENSHOTS to enabled.toString()))
        mutablePolicy.value = mutablePolicy.value.copy(blockScreenshots = enabled)
    }

    override suspend fun setLockOnBackground(enabled: Boolean) = withContext(Dispatchers.IO) {
        storage.write(mapOf(KEY_LOCK_ON_BACKGROUND to enabled.toString()))
        mutablePolicy.value = mutablePolicy.value.copy(lockOnBackground = enabled)
    }

    private companion object {
        const val KEY_BLOCK_SCREENSHOTS = "security.block_screenshots"
        const val KEY_LOCK_ON_BACKGROUND = "security.lock_on_background"
    }
}
