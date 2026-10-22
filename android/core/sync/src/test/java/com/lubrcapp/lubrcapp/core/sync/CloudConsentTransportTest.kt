package com.lubrcapp.lubrcapp.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test
    fun consentRevokedBlocksExchangeBeforeAnyRequest() {
        val transport = AuthenticatedVpsTransport(
            "https://example.invalid",
            DeviceTokenProvider { "token" },
            setOf("products"),
            canExchange = { false },
        )
        val batch = SyncBatch(
            SyncIdentity("tenant", "business", "device"),
            listOf(
                SyncChange(
                    "op-1", "products", "entity-1", OperationType.CREATE, 1,
                    """{"uuid":"entity-1"}""", 100,
                )
            ),
            null,
        )
        // The consent check runs before any connection is opened, so a revoked
        // consent fails the exchange deterministically with no network traffic.
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { transport.exchange(batch) }
        }
        assertEquals("Cloud sync consent revoked", failure.message)
    }
}
