package com.lubrcapp.lubrcapp.core.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exchange-level transport verification through the connection seam
 * (12C-04 pull validation, 12C-07 mid-flight revocation). Robolectric
 * supplies the real org.json implementation the transport parses with;
 * the scripted connections supply the VPS responses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthenticatedVpsTransportExchangeTest {
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")

    private class FakeHttpURLConnection(
        url: URL,
        private val status: Int,
        private val responseBody: String,
        private val onBodyWritten: () -> Unit = {},
    ) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getOutputStream(): OutputStream = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                onBodyWritten()
            }
        }
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
        override fun getResponseCode(): Int = status
    }

    private fun transport(
        connections: MutableList<FakeHttpURLConnection>,
        pushBody: String = """{"applied":[{"table":"products","uuid":"p-1"}],"rejected":[]}""",
        pullBody: String,
        canExchange: () -> Boolean = { true },
        onPushWritten: () -> Unit = {},
    ): AuthenticatedVpsTransport = AuthenticatedVpsTransport(
        "https://vps.example",
        DeviceTokenProvider { "token-1" },
        setOf("products"),
        canExchange = canExchange,
        connectionFactory = { url ->
            val fake = if (url.toString().contains("/push")) {
                FakeHttpURLConnection(url, 200, pushBody, onPushWritten)
            } else {
                FakeHttpURLConnection(url, 200, pullBody)
            }
            connections += fake
            fake
        },
    )

    private fun change() = SyncChange(
        "op-1", "products", "p-1", OperationType.CREATE, 1,
        """{"uuid":"p-1","name":"Local"}""", 100L,
    )

    @Test fun exchangePushesPullsAndReturnsReceiptsThroughTheSeam() = runBlocking {
        val connections = mutableListOf<FakeHttpURLConnection>()
        val transport = transport(
            connections,
            pullBody = """{"changes":{"products":[{"uuid":"p-9","name":"Oil","updated_at":"2026-01-01"}]},"cursor":7}""",
        )
        val response = transport.exchange(SyncBatch(identity, listOf(change()), "3"))
        assertEquals(2, connections.size)
        assertEquals(listOf(SyncOutcome.ACCEPTED), response.receipts.map { it.outcome })
        assertEquals(listOf("p-9"), response.remoteChanges.map { it.entityId })
        assertEquals("7", response.nextCursor)
    }

    @Test fun consentRevokedMidExchangeStopsBeforePull() {
        val connections = mutableListOf<FakeHttpURLConnection>()
        var consent = true
        val transport = transport(
            connections,
            pullBody = """{"changes":{},"cursor":0}""",
            canExchange = { consent },
            onPushWritten = { consent = false },
        )
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { transport.exchange(SyncBatch(identity, listOf(change()), "0")) }
        }
        assertTrue(error.message?.contains("consent revoked") == true)
        assertEquals("pull must never be attempted after revocation", 1, connections.size)
    }

    @Test fun regressingServerCursorIsRejected() {
        val connections = mutableListOf<FakeHttpURLConnection>()
        val transport = transport(connections, pullBody = """{"changes":{},"cursor":4}""")
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transport.exchange(SyncBatch(identity, emptyList(), "9")) }
        }
        assertTrue(error.message?.contains("backwards") == true)
    }

    @Test fun unexpectedTableInPullResponseIsRejected() {
        val connections = mutableListOf<FakeHttpURLConnection>()
        val transport = transport(
            connections,
            pullBody = """{"changes":{"audit_log":[{"uuid":"x-1"}]},"cursor":1}""",
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transport.exchange(SyncBatch(identity, emptyList(), "0")) }
        }
        assertTrue(error.message?.contains("Unexpected VPS table") == true)
    }

    @Test fun malformedStoredCursorIsRejectedBeforeAnyRequest() {
        val connections = mutableListOf<FakeHttpURLConnection>()
        val transport = transport(connections, pullBody = """{"changes":{},"cursor":0}""")
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { transport.exchange(SyncBatch(identity, emptyList(), "not-a-number")) }
        }
        assertTrue(error.message?.contains("Invalid stored VPS cursor") == true)
        assertTrue(connections.isEmpty())
    }
}
