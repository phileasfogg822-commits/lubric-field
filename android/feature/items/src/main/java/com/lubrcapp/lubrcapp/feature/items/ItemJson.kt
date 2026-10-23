package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 15.1 — codec between [Item] and the JSON payload of a
 * "products" operational record.
 *
 * Decode is deliberately tolerant, because the table predates the
 * typed model: Phase 12 wrote free-form payloads (typically just
 * uuid + name from the generic form), and one bad record must never
 * take down the whole item list. Rules:
 *
 * - Unknown or missing fields default; unparseable payloads decode
 *   to a minimal item carrying only the record id.
 * - The record id is authoritative: a "uuid" key inside legacy
 *   payloads is ignored, never trusted over the store's id.
 * - Numbers are accepted as JSON numbers or as strings (older
 *   writers were not consistent); amounts round-trip as plain
 *   strings so [BigDecimal] precision survives exactly.
 *
 * Encode writes the full typed field set, omitting nulls, so any
 * record saved through the typed model re-encodes cleanly.
 */
object ItemJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(item: Item): String {
        val obj = buildJsonObject {
            put("name", item.name)
            put("type", item.type.name)
            item.sku?.let { put("sku", it) }
            item.barcode?.let { put("barcode", it) }
            item.categoryId?.let { put("categoryId", it) }
            item.brandId?.let { put("brandId", it) }
            item.unit?.let { put("unit", it) }
            item.purchaseRate?.let { put("purchaseRate", it.toPlainString()) }
            item.saleRate?.let { put("saleRate", it.toPlainString()) }
            item.wholesaleRate?.let { put("wholesaleRate", it.toPlainString()) }
            item.dealerRate?.let { put("dealerRate", it.toPlainString()) }
            item.minimumRate?.let { put("minimumRate", it.toPlainString()) }
            item.mrp?.let { put("mrp", it.toPlainString()) }
            item.taxPercent?.let { put("taxPercent", it.toPlainString()) }
            item.openingStock?.let { put("openingStock", it.toPlainString()) }
            item.description?.let { put("description", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): Item {
        val root = parseObject(payload) ?: return Item(id = id, name = "")
        return Item(
            id = id,
            name = root.string("name") ?: "",
            type = decodeType(root.string("type")),
            sku = root.string("sku"),
            barcode = root.string("barcode"),
            categoryId = root.string("categoryId"),
            brandId = root.string("brandId"),
            unit = root.string("unit"),
            purchaseRate = root.decimal("purchaseRate"),
            saleRate = root.decimal("saleRate"),
            wholesaleRate = root.decimal("wholesaleRate"),
            dealerRate = root.decimal("dealerRate"),
            minimumRate = root.decimal("minimumRate"),
            mrp = root.decimal("mrp"),
            taxPercent = root.decimal("taxPercent"),
            openingStock = root.decimal("openingStock"),
            description = root.string("description"),
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

    private fun decodeType(raw: String?): ItemType {
        if (raw == null) return ItemType.PRODUCT
        return try {
            ItemType.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            ItemType.PRODUCT
        }
    }

    /** The primitive at [key], or null when absent or an explicit JSON null. */
    private fun JsonObject.primitive(key: String): JsonPrimitive? {
        val element = this[key] ?: return null
        if (element is kotlinx.serialization.json.JsonNull) return null
        return element as? JsonPrimitive
    }

    private fun JsonObject.string(key: String): String? {
        val primitive = primitive(key) ?: return null
        // Tolerate numbers/booleans written where a string is expected.
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
