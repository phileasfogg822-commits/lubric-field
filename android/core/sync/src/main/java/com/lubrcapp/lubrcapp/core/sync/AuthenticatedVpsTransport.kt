package com.lubrcapp.lubrcapp.core.sync

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Token comes from a secure, device-scoped credential store; never from the APK. */
fun interface DeviceTokenProvider {
    suspend fun token(): String?
}

/**
 * Adapter for the existing shop-scoped VPS /push and /pull protocol.
 * Only explicitly mapped entity types may be transmitted; unknown entities fail closed.
 */
class AuthenticatedVpsTransport(
    private val baseUrl: String,
    private val tokenProvider: DeviceTokenProvider,
    private val allowedTables: Set<String>,
    private val canExchange: () -> Boolean = { true }, // checked before each HTTP request
) : SyncTransport {
    init {
        require(baseUrl.startsWith("https://")) { "VPS synchronization requires HTTPS" }
        require(allowedTables.all { it.matches(Regex("[a-z][a-z0-9_]*")) })
    }

    override suspend fun exchange(batch: SyncBatch): SyncResponse = withContext(Dispatchers.IO) {
        SyncContract.validateBatch(batch)
        val token = tokenProvider.token()?.takeIf { it.isNotBlank() }
            ?: error("Device is not paired with the VPS")
        val changes = JSONArray()
        require(batch.changes.map { it.entityType to it.entityId }.distinct().size == batch.changes.size) {
            "Legacy VPS receipts are keyed by table and UUID; duplicate entity changes cannot be acknowledged safely"
        }
        batch.changes.forEach { change ->
            require(change.entityType in allowedTables) { "Unsupported VPS entity type" }
            require(change.operation != OperationType.SYNC) { "SYNC is not a VPS row operation" }
            val row = change.payload?.let { JSONObject(it) } ?: JSONObject()
            changes.put(JSONObject()
                .put("table", change.entityType)
                .put("uuid", change.entityId)
                .put("op", if (change.operation == OperationType.DELETE) "delete" else "insert")
                .put("row", row))
        }
        val receipts = if (changes.length() > 0) {
            val result = request("POST", "/api/v1/push", token, JSONObject().put("changes", changes))
            val applied = result.getJSONArray("applied")
            val rejected = result.getJSONArray("rejected")
            batch.changes.map { change ->
                val accepted = (0 until applied.length()).any { index ->
                    val item = applied.getJSONObject(index)
                    item.getString("uuid") == change.entityId &&
                        item.getString("table") == change.entityType
                }
                val refused = (0 until rejected.length()).any { index ->
                    rejected.getJSONObject(index).optString("uuid") == change.entityId
                }
                SyncReceipt(change.operationId, when {
                    refused -> SyncOutcome.REJECTED
                    accepted -> SyncOutcome.ACCEPTED
                    else -> SyncOutcome.CONFLICT
                })
            }
        } else emptyList()
        val since = batch.cursor?.toLongOrNull() ?: if (batch.cursor == null) 0L
            else error("Invalid stored VPS cursor")
        require(since >= 0L)
        val response = request("GET", "/api/v1/pull?since=$since", token, null)
        val remote = mutableListOf<SyncChange>()
        val tables = response.getJSONObject("changes")
        val names = tables.keys()
        while (names.hasNext()) {
            val table = names.next()
            require(table in allowedTables) { "Unexpected VPS table in response" }
            val rows = tables.getJSONArray(table)
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val uuid = row.getString("uuid")
                remote += SyncChange(
                    operationId = "$table:$uuid:${row.optString("updated_at")}",
                    entityType = table,
                    entityId = uuid,
                    operation = OperationType.UPDATE,
                    revision = 0L,
                    payload = row.toString(),
                    occurredAt = System.currentTimeMillis(),
                )
            }
        }
        val cursor = response.getLong("cursor")
        require(cursor >= since) { "VPS cursor moved backwards" }
        SyncResponse(receipts, remote, cursor.toString())
    }

    private fun request(method: String, path: String, token: String, body: JSONObject?): JSONObject {
        check(canExchange()) { "Cloud sync consent revoked" }
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status in 200..299) { "VPS synchronization failed (HTTP $status)" }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
