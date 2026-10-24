package com.lubrcapp.lubrcapp.feature.inventory

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 19.1 — codec between [StockMovementRecord] and the JSON
 * payload of a "stock_movements" operational record. Same
 * tolerance contract as the Phase 14–18 codecs: unknown/missing
 * fields default, unparseable payloads decode to a minimal
 * record carrying only the record id, and the record id always
 * wins over any in-payload id. An unknown or missing type
 * decodes as ADJUSTMENT_IN with quantity ZERO — the historical
 * raw rows Phase 12 wrote carry no type, and a zero movement
 * changes no balance; the type is corrected only by a real
 * movement, never guessed here. Amounts serialize as plain
 * strings so a movement round-trips exactly.
 */
object StockMovementJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: StockMovementRecord): String {
        val obj = buildJsonObject {
            put("itemId", record.itemId)
            record.warehouseId?.let { put("warehouseId", it) }
            record.batchId?.let { put("batchId", it) }
            put("type", record.type.name)
            put("quantity", record.quantity.toPlainString())
            record.rate?.let { put("rate", it.toPlainString()) }
            record.referenceType?.let { put("referenceType", it) }
            record.referenceId?.let { put("referenceId", it) }
            put("date", record.date)
            put("reason", record.reason)
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): StockMovementRecord {
        val root = parseObject(payload)
            ?: return StockMovementRecord(
                id = id,
                itemId = "",
                type = MovementType.ADJUSTMENT_IN,
                quantity = BigDecimal.ZERO,
            )
        return StockMovementRecord(
            id = id,
            itemId = root.string("itemId") ?: "",
            warehouseId = root.string("warehouseId"),
            batchId = root.string("batchId"),
            type = root.string("type")?.let { name ->
                MovementType.entries.firstOrNull { it.name == name }
            } ?: MovementType.ADJUSTMENT_IN,
            quantity = root.decimal("quantity") ?: BigDecimal.ZERO,
            rate = root.decimal("rate"),
            referenceType = root.string("referenceType"),
            referenceId = root.string("referenceId"),
            date = root.string("date") ?: "",
            reason = root.string("reason") ?: "",
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

    private fun JsonObject.decimal(key: String): BigDecimal? {
        val content = string(key) ?: return null
        return try {
            BigDecimal(content)
        } catch (e: Exception) {
            null
        }
    }
}
