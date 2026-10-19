package com.lubrcapp.lubrcapp

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Resolves the persisted device identity before exposing business-scoped operations. */
@HiltViewModel
class OperationalScopeViewModel @Inject constructor(
    private val deviceIdentityProvider: DeviceIdentityProvider,
) : ViewModel() {
    suspend fun resolve(context: TenantContext): OperationalScope =
        OperationalScope(
            tenantId = context.tenantId.value,
            businessId = context.businessId.value,
            deviceId = deviceIdentityProvider.getOrCreate().value,
        )
}
