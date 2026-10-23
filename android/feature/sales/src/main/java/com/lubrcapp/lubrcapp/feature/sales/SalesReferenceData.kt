package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A party the document editor can pick (from "customers" records). */
data class PartyOption(val id: String, val name: String)

/** An item the line editor can pick (from "products" records). */
data class ItemOption(
    val id: String,
    val name: String,
    val saleRate: BigDecimal?,
    val taxPercent: BigDecimal?,
)

/**
 * Phase 18.4 — minimal tolerant reads of OTHER modules' master
 * records for the editor's pickers. feature/sales deliberately
 * does not depend on feature/parties or feature/items (their
 * codecs are not shared libraries), so this reads only the few
 * fields the pickers need, with the usual tolerance: a record
 * with no readable name falls back to its id, and unreadable
 * amounts come back null rather than failing the list.
 */
object SalesReferenceData {

    private val json = Json { ignoreUnknownKeys = true }

    fun partyOptions(records: List<OperationalRecord>): List<PartyOption> =
        records.map { record ->
            val root = parseObject(record.json)
            PartyOption(id = record.id, name = root?.string("name")?.takeIf { it.isNotBlank() } ?: record.id)
        }

    fun itemOptions(records: List<OperationalRecord>): List<ItemOption> =
        records.map { record ->
            val root = parseObject(record.json)
            ItemOption(
                id = record.id,
                name = root?.string("name")?.takeIf { it.isNotBlank() } ?: record.id,
                saleRate = root?.decimal("saleRate"),
                taxPercent = root?.decimal("taxPercent"),
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
