package com.lubrcapp.lubrcapp.core.sync

import org.junit.Assert.assertThrows
import org.junit.Test

class CloudConsentTransportTest {
    @Test
    fun transportRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedVpsTransport("http://localhost", DeviceTokenProvider { "token" }, setOf("products"))
        }
    }

    @Test
    fun transportRejectsMalformedAllowedTables() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedVpsTransport("https://example.invalid", DeviceTokenProvider { "token" }, setOf("products;DROP"))
        }
    }
}
