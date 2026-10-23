package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 18.1 — codec between [SalesDocumentLine] and the JSON
 * payload of an "order_items" operational record. Same tolerance
 * contract as [SalesDocumentJson]; amounts accept JSON numbers or
 * strings and always encode as plain strings, and a tax component
 * with a blank name or an unreadable rate is dropped rather than
 * failing the line.
 */
object SalesDocumentLineJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: SalesDocumentLine): String {
        val obj = buildJsonObject {
            put("orderId", record.orderId)
            put("position", record.position)
            record.itemId?.let { put("itemId", it) }
            put("description", record.description)
            put("quantity", record.quantity.toPlainString())
            record.unitId?.let { put("unitId", it) }
            put("rate", record.rate.toPlainString())
            record.discountKind?.let { put("discountKind", it) }
            record.discountValue?.let { put("discountValue", it.toPlainString()) }
            put("taxInclusive", record.taxInclusive)
            put("taxComponents", buildJsonArray {
                record.taxComponents.forEach { component ->
                    add(buildJsonObject {
                        put("name", component.name)
                        put("rate", component.ratePercent.toPlainString())
                    })
                }
            })
            record.warehouseId?.let { put("warehouseId", it) }
            record.batchId?.let { put("batchId", it) }
            put("fulfilledQuantity", record.fulfilledQuantity.toPlainString())
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): SalesDocumentLine {
        val fallback = SalesDocumentLine(
            id = id,
            orderId = "",
            quantity = BigDecimal.ZERO,
            rate = BigDecimal.ZERO,
        )
        val root = parseObject(payload) ?: return fallback
        return SalesDocumentLine(
            id = id,
            orderId = root.string("orderId") ?: "",
            position = root.int("position") ?: 0,
            itemId = root.string("itemId"),
            description = root.string("description") ?: "",
            quantity = root.decimal("quantity") ?: BigDecimal.ZERO,
            unitId = root.string("unitId"),
            rate = root.decimal("rate") ?: BigDecimal.ZERO,
            discountKind = root.string("discountKind"),
            discountValue = root.decimal("discountValue"),
            taxInclusive = root.boolean("taxInclusive") ?: false,
            taxComponents = root.taxComponents(),
            warehouseId = root.string("warehouseId"),
            batchId = root.string("batchId"),
            fulfilledQuantity = root.decimal("fulfilledQuantity") ?: BigDecimal.ZERO,
        )
    }

    private fun JsonObject.taxComponents(): List<SalesTaxComponent> {
        val array = this["taxComponents"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rate = obj.decimal("rate") ?: return@mapNotNull null
            SalesTaxComponent(name, rate)
        }
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
        val primitive = primitive(key) ?: return null
        return try {
            BigDecimal(primitive.content)
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.int(key: String): Int? {
        val primitive = primitive(key) ?: return null
        return primitive.content.toIntOrNull()
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = primitive(key) ?: return null
        return primitive.content.toBooleanStrictOrNull()
    }
}
