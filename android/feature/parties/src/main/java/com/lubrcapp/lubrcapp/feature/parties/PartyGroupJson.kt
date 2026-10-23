package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 14.3 — codec between [PartyGroup] and the JSON payload of a
 * "customer_groups" operational record. Same tolerance contract as
 * [PartyJson]: unknown/missing fields default, unparseable payloads
 * decode to a minimal group carrying only the record id, the record
 * id always wins over any in-payload id, and the discount round-trips
 * as a plain string so [BigDecimal] precision survives exactly.
 */
object PartyGroupJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(group: PartyGroup): String {
        val obj = buildJsonObject {
            put("name", group.name)
            group.priceLevel?.let { put("priceLevel", it) }
            group.discountPercent?.let { put("discountPercent", it.toPlainString()) }
            group.paymentTerms?.let { put("paymentTerms", it) }
            group.taxRule?.let { put("taxRule", it) }
            group.salesperson?.let { put("salesperson", it) }
            group.territory?.let { put("territory", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): PartyGroup {
        val root = parseObject(payload) ?: return PartyGroup(id = id, name = "")
        return PartyGroup(
            id = id,
            name = root.string("name") ?: "",
            priceLevel = root.string("priceLevel"),
            discountPercent = root.decimal("discountPercent"),
            paymentTerms = root.string("paymentTerms"),
            taxRule = root.string("taxRule"),
            salesperson = root.string("salesperson"),
            territory = root.string("territory"),
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
