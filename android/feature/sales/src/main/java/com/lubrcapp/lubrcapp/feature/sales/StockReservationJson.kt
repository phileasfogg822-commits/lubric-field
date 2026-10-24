package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 21.2 — codec between [StockReservationRecord] and the
 * JSON payload of a "reservations" operational record. Same
 * tolerance contract as the 18.1 codec: unknown/missing fields
 * default, unparseable payloads decode to a minimal record
 * carrying only the record id, and the record id always wins
 * over any in-payload id. An unknown or missing status decodes
 * as ACTIVE (a reservation holds stock until explicitly
 * released); an unparseable quantity decodes as ZERO.
 */
object StockReservationJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: StockReservationRecord): String {
        val obj = buildJsonObject {
            put("orderId", record.orderId)
            put("lineId", record.lineId)
            put("itemId", record.itemId)
            record.warehouseId?.let { put("warehouseId", it) }
            record.batchId?.let { put("batchId", it) }
            put("quantity", record.quantity.toPlainString())
            put("status", record.status.name)
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): StockReservationRecord {
        val root = parseObject(payload) ?: return StockReservationRecord(
            id = id,
            orderId = "",
            lineId = "",
            itemId = "",
            quantity = BigDecimal.ZERO,
        )
        return StockReservationRecord(
            id = id,
            orderId = root.string("orderId") ?: "",
            lineId = root.string("lineId") ?: "",
            itemId = root.string("itemId") ?: "",
            warehouseId = root.string("warehouseId"),
            batchId = root.string("batchId"),
            quantity = root.string("quantity")?.let { text ->
                runCatching { BigDecimal(text) }.getOrDefault(BigDecimal.ZERO)
            } ?: BigDecimal.ZERO,
            status = root.string("status")?.let { name ->
                ReservationStatus.entries.firstOrNull { it.name == name }
            } ?: ReservationStatus.ACTIVE,
            createdAt = root.string("createdAt") ?: "",
        )
    }

    private fun parseObject(payload: String): JsonObject? {
        if (payload.isBlank()) return null
        val element: JsonElement = try {
            json.parseToJsonElement(payload)
        } catch (e: Exception) {
            return null
        }
        return element as? JsonObject
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return element as? JsonPrimitive
    }

    private fun JsonObject.string(key: String): String? {
        val primitive = primitive(key) ?: return null
        return primitive.content
    }
}
