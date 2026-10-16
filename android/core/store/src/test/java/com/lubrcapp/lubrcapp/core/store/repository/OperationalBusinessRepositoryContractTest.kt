package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalBusinessRepositoryContractTest {
    @Test fun scopeRetainsTenantBusinessAndDevice() {
        val scope = OperationalScope("tenant-a", "business-b", "device-c")
        assertEquals("tenant-a", scope.tenantId)
        assertEquals("business-b", scope.businessId)
        assertEquals("device-c", scope.deviceId)
    }
}
