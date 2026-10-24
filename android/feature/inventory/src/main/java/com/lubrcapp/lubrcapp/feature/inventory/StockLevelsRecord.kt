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
 * Phase 19.3 — §33 minimum / reorder / maximum levels for one
 * item, stored as a "stock_levels" record keyed by the item id.
 * The table is NOT in the Phase 12 sync whitelist → local-only,
 * the numbering / timeline posture: levels are device-side
 * working data, while stock truth lives in the movements, which
 * do sync. Any level may be unset (null) — an unset level simply
 * does not participate in the §32 status derivation.
 */
data class StockLevelsRecord(
    val itemId: String,
    val minimum: BigDecimal? = null,
    val reorderLevel: BigDecimal? = null,
    val maximum: BigDecimal? = null,
)

object StockLevelsJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: StockLevelsRecord): String {
        val obj = buildJsonObject {
            record.minimum?.let { put("minimum", it.toPlainString()) }
            record.reorderLevel?.let { put("reorderLevel", it.toPlainString()) }
            record.maximum?.let { put("maximum", it.toPlainString()) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(itemId: String, payload: String): StockLevelsRecord {
        val root = parseObject(payload) ?: return StockLevelsRecord(itemId = itemId)
        return StockLevelsRecord(
            itemId = itemId,
            minimum = root.decimal("minimum"),
            reorderLevel = root.decimal("reorderLevel"),
            maximum = root.decimal("maximum"),
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

    private fun JsonObject.decimal(key: String): BigDecimal? {
        val primitive = primitive(key) ?: return null
        return try {
            BigDecimal(primitive.content)
        } catch (e: Exception) {
            null
        }
    }
}
