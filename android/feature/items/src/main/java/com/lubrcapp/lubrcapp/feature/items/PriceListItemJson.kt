package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 16.3 — codec between [PriceListItem] and the JSON payload of a
 * "price_list_items" operational record. Same tolerance contract as
 * [ItemJson]: unknown/missing fields default, unparseable payloads
 * decode to a minimal record carrying only the record id, and the
 * record id always wins over any in-payload id.
 */
object PriceListItemJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: PriceListItem): String {
        val obj = buildJsonObject {
            put("priceListId", record.priceListId)
            put("itemId", record.itemId)
            record.rate?.let { put("rate", it.toPlainString()) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): PriceListItem {
        val root = parseObject(payload) ?: return PriceListItem(id = id, priceListId = "", itemId = "", rate = null)
        return PriceListItem(
            id = id,
            priceListId = root.string("priceListId") ?: "",
            itemId = root.string("itemId") ?: "",
            rate = root.decimal("rate"),
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

    private fun JsonObject.decimal(key: String): BigDecimal? {
        val primitive = primitive(key) ?: return null
        return try {
            BigDecimal(primitive.content)
        } catch (e: Exception) {
            null
        }
    }
}
