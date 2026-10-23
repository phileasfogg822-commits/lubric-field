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
 * Phase 16.1 — codec between [UnitConversion] and the JSON payload
 * of a "unit_conversions" operational record. Same tolerance
 * contract as [ItemJson]: the record id wins over any in-payload
 * id, numbers are accepted as JSON numbers or strings, and the
 * factor round-trips as a plain string so [BigDecimal] precision
 * survives. A missing or unreadable factor decodes as the zero
 * sentinel documented on [UnitConversion] — the conversion rule
 * never converts with a non-positive factor.
 */
object UnitConversionJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(conversion: UnitConversion): String {
        val obj = buildJsonObject {
            put("fromUnitId", conversion.fromUnitId)
            put("toUnitId", conversion.toUnitId)
            put("factor", conversion.factor.toPlainString())
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): UnitConversion {
        val root = parseObject(payload)
            ?: return UnitConversion(id = id, fromUnitId = "", toUnitId = "", factor = BigDecimal.ZERO)
        return UnitConversion(
            id = id,
            fromUnitId = root.string("fromUnitId") ?: "",
            toUnitId = root.string("toUnitId") ?: "",
            factor = root.decimal("factor") ?: BigDecimal.ZERO,
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
