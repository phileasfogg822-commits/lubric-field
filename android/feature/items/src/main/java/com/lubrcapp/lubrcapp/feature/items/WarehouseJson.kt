package com.lubrcapp.lubrcapp.feature.items

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 16.2 — codec between [Warehouse] and the JSON payload of a
 * "warehouses" operational record. Same tolerance contract as
 * [ItemJson]: unknown/missing fields default, unparseable payloads
 * decode to a minimal record carrying only the record id, and the
 * record id always wins over any in-payload id.
 */
object WarehouseJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: Warehouse): String {
        val obj = buildJsonObject {
            put("name", record.name)
            record.branchId?.let { put("branchId", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): Warehouse {
        val root = parseObject(payload) ?: return Warehouse(id = id, name = "")
        return Warehouse(
            id = id,
            name = root.string("name") ?: "",
            branchId = root.string("branchId"),
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

    /** The primitive at [key], or null when absent or an explicit JSON null. */
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
