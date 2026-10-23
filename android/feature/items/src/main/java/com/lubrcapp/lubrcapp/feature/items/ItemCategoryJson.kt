package com.lubrcapp.lubrcapp.feature.items

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 15.3 — codec between [ItemCategory] and the JSON payload of
 * a "categories" operational record. Same tolerance contract as
 * [ItemJson]: unknown/missing fields default, unparseable payloads
 * decode to a minimal category carrying only the record id, and the
 * record id always wins over any in-payload id.
 */
object ItemCategoryJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(category: ItemCategory): String {
        val obj = buildJsonObject {
            put("name", category.name)
            category.parentId?.let { put("parentId", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): ItemCategory {
        val root = parseObject(payload) ?: return ItemCategory(id = id, name = "")
        return ItemCategory(
            id = id,
            name = root.string("name") ?: "",
            parentId = root.string("parentId"),
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
