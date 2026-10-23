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
 * Phase 16.4 — codec between [Batch] and the JSON payload of a
 * "batches" operational record. Same tolerance contract as
 * [ItemJson]: unknown/missing fields default, numbers are
 * accepted as JSON numbers or strings and round-trip as plain
 * strings so [BigDecimal] precision survives, unparseable
 * payloads decode to a minimal batch carrying only the record
 * id, and the record id always wins over any in-payload id.
 */
object BatchJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(batch: Batch): String {
        val obj = buildJsonObject {
            put("itemId", batch.itemId)
            put("batchNumber", batch.batchNumber)
            batch.manufacturingDate?.let { put("manufacturingDate", it) }
            batch.expiryDate?.let { put("expiryDate", it) }
            batch.purchaseRate?.let { put("purchaseRate", it.toPlainString()) }
            batch.saleRate?.let { put("saleRate", it.toPlainString()) }
            batch.mrp?.let { put("mrp", it.toPlainString()) }
            batch.quantity?.let { put("quantity", it.toPlainString()) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): Batch {
        val root = parseObject(payload)
            ?: return Batch(id = id, itemId = "", batchNumber = "")
        return Batch(
            id = id,
            itemId = root.string("itemId") ?: "",
            batchNumber = root.string("batchNumber") ?: "",
            manufacturingDate = root.string("manufacturingDate"),
            expiryDate = root.string("expiryDate"),
            purchaseRate = root.decimal("purchaseRate"),
            saleRate = root.decimal("saleRate"),
            mrp = root.decimal("mrp"),
            quantity = root.decimal("quantity"),
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
