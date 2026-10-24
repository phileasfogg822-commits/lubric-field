package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** An item the inventory screen can show stock for (from "products" records). */
data class ItemStockOption(
    val id: String,
    val name: String,
    val categoryId: String? = null,
    val brandId: String? = null,
    /** The §26 cost rate — the rate inventory value is computed at (§31). */
    val purchaseRate: BigDecimal? = null,
    /** The Phase 15 stored datum the ledger uses as its starting constant. */
    val openingStock: BigDecimal? = null,
)

/** A named master record (warehouse / category / brand) for filters and labels. */
data class NamedOption(val id: String, val name: String)

/**
 * Phase 19.3 — minimal tolerant reads of OTHER modules' master
 * records for the inventory screen. feature/inventory deliberately
 * does not depend on feature/items (its codecs are not shared
 * libraries), so this reads only the fields the screen needs,
 * with the usual tolerance: a record with no readable name falls
 * back to its id, and unreadable amounts come back null rather
 * than failing the list.
 */
object InventoryReferenceData {

    private val json = Json { ignoreUnknownKeys = true }

    fun itemOptions(records: List<OperationalRecord>): List<ItemStockOption> =
        records.map { record ->
            val root = parseObject(record.json)
            ItemStockOption(
                id = record.id,
                name = root?.string("name")?.takeIf { it.isNotBlank() } ?: record.id,
                categoryId = root?.string("categoryId"),
                brandId = root?.string("brandId"),
                purchaseRate = root?.decimal("purchaseRate"),
                openingStock = root?.decimal("openingStock"),
            )
        }

    fun namedOptions(records: List<OperationalRecord>): List<NamedOption> =
        records.map { record ->
            val root = parseObject(record.json)
            NamedOption(id = record.id, name = root?.string("name")?.takeIf { it.isNotBlank() } ?: record.id)
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
}
