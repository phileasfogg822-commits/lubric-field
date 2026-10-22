package com.lubrcapp.lubrcapp.feature.syncstatus

import com.lubrcapp.lubrcapp.core.repository.CloudPairingGateway
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusViewModelTest {
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeGateway : CloudPairingGateway {
        var paired = false
        var endpoint: String? = null
        val pairCalls = mutableListOf<Triple<OperationalScope, String, String>>()
        var unpairCalls = 0
        var pairFailure: Exception? = null

        override suspend fun isPaired(scope: OperationalScope) = paired
        override fun pairedEndpoint(scope: OperationalScope) = endpoint
        override suspend fun pair(scope: OperationalScope, endpoint: String, token: String) {
            pairFailure?.let { throw it }
            pairCalls.add(Triple(scope, endpoint, token))
            paired = true
            this.endpoint = endpoint
        }
        override suspend fun unpair(scope: OperationalScope) {
            unpairCalls++
            paired = false
            endpoint = null
        }
    }

    @Test fun refreshReflectsGatewayPairingState() = runBlocking {
        val fake = FakeGateway().apply { paired = true; endpoint = "https://vps.example" }
        val vm = SyncStatusViewModel(fake)
        vm.refresh(scope)
        assertTrue(vm.state.value.paired)
        assertEquals("https://vps.example", vm.state.value.endpoint)
        assertNull(vm.state.value.error)
    }

    @Test fun pairTrimsEndpointAndRefreshesState() = runBlocking {
        val fake = FakeGateway()
        val vm = SyncStatusViewModel(fake)
        vm.pair(scope, "  https://vps.example  ", "secret-token")
        assertEquals(
            listOf(Triple(scope, "https://vps.example", "secret-token")),
            fake.pairCalls,
        )
        assertTrue(vm.state.value.paired)
        assertNull(vm.state.value.error)
    }

    @Test fun pairFailureSurfacesErrorAndStaysUnpaired() = runBlocking {
        val fake = FakeGateway().apply {
            pairFailure = IllegalArgumentException("Cloud sync endpoint must use HTTPS")
        }
        val vm = SyncStatusViewModel(fake)
        vm.pair(scope, "http://vps.example", "secret-token")
        assertFalse(vm.state.value.paired)
        assertEquals("Cloud sync endpoint must use HTTPS", vm.state.value.error)
    }

    @Test fun unpairDelegatesAndRefreshesState() = runBlocking {
        val fake = FakeGateway().apply { paired = true; endpoint = "https://vps.example" }
        val vm = SyncStatusViewModel(fake)
        vm.refresh(scope)
        vm.unpair(scope)
        assertEquals(1, fake.unpairCalls)
        assertFalse(vm.state.value.paired)
        assertNull(vm.state.value.endpoint)
    }
}
