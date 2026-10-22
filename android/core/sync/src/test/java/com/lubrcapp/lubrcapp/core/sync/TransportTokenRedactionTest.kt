package com.lubrcapp.lubrcapp.core.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 12C-06 residual: token hygiene at the transport layer.
 *
 * The sync code contains no logging at all, so the leak surfaces that
 * exist are the request line, the headers, the request body, and the
 * error messages a caller (or a crash reporter) can observe. These tests
 * pin that the device token appears in exactly one place — the
 * Authorization header — and in no URL, body, result or error text,
 * across success and failure exchanges. The HTTP 401 case is also the
 * expired-token behavior: the client cannot distinguish an expired token
 * from a valid one until the VPS rejects it, and that rejection must
 * surface as a clean, token-free failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TransportTokenRedactionTest {
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")
    private val token = "SECRET-DEVICE-TOKEN-9f8e7d6c5b4a"

    /** Scripted connection that records everything the transport emits. */
    private class RecordingConnection(
        url: URL,
        private val status: Int,
        private val responseBody: String,
    ) : HttpURLConnection(url) {
        val requestProperties = LinkedHashMap<String, String>()
        val body = ByteArrayOutputStream()

        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun setRequestProperty(key: String, value: String) {
            requestProperties[key] = value
        }
        override fun getOutputStream(): OutputStream = body
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
        override fun getResponseCode(): Int = status
    }

    private fun transport(
        connections: MutableList<RecordingConnection>,
        tokenProvider: DeviceTokenProvider,
        pushStatus: Int = 200,
        pullStatus: Int = 200,
        canExchange: () -> Boolean = { true },
    ): AuthenticatedVpsTransport = AuthenticatedVpsTransport(
        "https://vps.example",
        tokenProvider,
        setOf("products"),
        canExchange = canExchange,
        connectionFactory = { url ->
            val connection = if (url.toString().contains("/push")) {
                RecordingConnection(url, pushStatus,
                    """{"applied":[{"table":"products","uuid":"p-1"}],"rejected":[]}""")
            } else {
                RecordingConnection(url, pullStatus,
                    """{"changes":{"products":[{"uuid":"p-9","name":"Oil","updated_at":"2026-01-01"}]},"cursor":7}""")
            }
            connections += connection
            connection
        },
    )

    private fun change() = SyncChange(
        "op-1", "products", "p-1", OperationType.CREATE, 1,
        """{"uuid":"p-1","name":"Local"}""", 100L,
    )

    @Test fun tokenAppearsOnlyInAuthorizationHeaderOnSuccess() = runBlocking {
        val connections = mutableListOf<RecordingConnection>()
        val response = transport(connections, DeviceTokenProvider { token })
            .exchange(SyncBatch(identity, listOf(change()), "3"))
        assertEquals(2, connections.size)
        for (connection in connections) {
            // The one legitimate surface: the Authorization header.
            assertEquals("Bearer $token", connection.requestProperties["Authorization"])
            // Nowhere else: not the URL, not any other header, not the body.
            assertFalse(connection.url.toString().contains(token))
            connection.requestProperties.forEach { (key, value) ->
                if (key != "Authorization") {
                    assertFalse("header $key must not carry the token", value.contains(token))
                }
            }
            assertFalse(connection.body.toString(Charsets.UTF_8.name()).contains(token))
        }
        // And never in anything the exchange hands back to the caller.
        assertFalse(response.receipts.toString().contains(token))
        assertFalse(response.remoteChanges.toString().contains(token))
        assertFalse(response.nextCursor.orEmpty().contains(token))
    }

    @Test fun expiredTokenFailsWithRedactedError() {
        val connections = mutableListOf<RecordingConnection>()
        // An expired token is a valid-looking string the VPS rejects: 401.
        val failing = transport(connections, DeviceTokenProvider { token }, pushStatus = 401)
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { failing.exchange(SyncBatch(identity, listOf(change()), "0")) }
        }
        assertTrue(error.message?.contains("HTTP 401") == true)
        assertFalse("error text must not carry the token",
            error.message.orEmpty().contains(token))
        // The rejection stopped the exchange: no pull was attempted.
        assertEquals(1, connections.size)
    }

    @Test fun missingOrBlankTokenFailsBeforeAnyRequest() {
        val connections = mutableListOf<RecordingConnection>()
        for (provider in listOf(
            DeviceTokenProvider { null },
            DeviceTokenProvider { "   " },
        )) {
            val unpaired = transport(connections, provider)
            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking { unpaired.exchange(SyncBatch(identity, listOf(change()), "0")) }
            }
            assertTrue(error.message?.contains("not paired") == true)
            assertFalse(error.message.orEmpty().contains(token))
        }
        assertTrue("no connection may be opened without a token", connections.isEmpty())
    }

    @Test fun consentRevokedErrorContainsNoToken() {
        val connections = mutableListOf<RecordingConnection>()
        val revoked = transport(
            connections, DeviceTokenProvider { token }, canExchange = { false })
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { revoked.exchange(SyncBatch(identity, listOf(change()), "0")) }
        }
        assertTrue(error.message?.contains("consent revoked") == true)
        assertFalse(error.message.orEmpty().contains(token))
        assertTrue(connections.isEmpty())
    }
}
