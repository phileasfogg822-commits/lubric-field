package com.lubrcapp.lubrcapp.feature.sales

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 18.1 — codec between [SalesDocument] and the JSON payload
 * of an "orders" operational record. Same tolerance contract as the
 * Phase 14–16 codecs: unknown/missing fields default, unparseable
 * payloads decode to a minimal record carrying only the record id,
 * and the record id always wins over any in-payload id. An unknown
 * or missing type decodes as SALES_ORDER — this table historically
 * held orders.
 */
object SalesDocumentJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: SalesDocument): String {
        val obj = buildJsonObject {
            put("type", record.type.name)
            put("number", record.number)
            record.partyId?.let { put("partyId", it) }
            put("date", record.date)
            record.dueDate?.let { put("dueDate", it) }
            put("status", record.status)
            put("notes", record.notes)
            record.convertedFromId?.let { put("convertedFromId", it) }
            record.convertedToId?.let { put("convertedToId", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): SalesDocument {
        val root = parseObject(payload) ?: return SalesDocument(id = id, type = SalesDocumentType.SALES_ORDER)
        return SalesDocument(
            id = id,
            type = root.string("type")?.let { name ->
                SalesDocumentType.entries.firstOrNull { it.name == name }
            } ?: SalesDocumentType.SALES_ORDER,
            number = root.string("number") ?: "",
            partyId = root.string("partyId"),
            date = root.string("date") ?: "",
            dueDate = root.string("dueDate"),
            status = root.string("status") ?: "DRAFT",
            notes = root.string("notes") ?: "",
            convertedFromId = root.string("convertedFromId"),
            convertedToId = root.string("convertedToId"),
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
}
