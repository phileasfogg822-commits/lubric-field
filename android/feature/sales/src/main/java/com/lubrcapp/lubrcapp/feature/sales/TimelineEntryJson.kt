package com.lubrcapp.lubrcapp.feature.sales

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 18.4 — codec between [TimelineEntry] and the JSON
 * payload of a "timeline_entries" operational record. Same
 * tolerance contract as the other codecs: unknown/missing
 * fields default, unparseable payloads decode to a minimal
 * record carrying only the record id, and the record id
 * always wins over any in-payload id.
 */
object TimelineEntryJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: TimelineEntry): String {
        val obj = buildJsonObject {
            put("documentId", record.documentId)
            put("documentType", record.documentType)
            put("documentNumber", record.documentNumber)
            record.partyId?.let { put("partyId", it) }
            put("action", record.action)
            put("grandTotal", record.grandTotal)
            put("timestamp", record.timestamp)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): TimelineEntry {
        val root = parseObject(payload) ?: return TimelineEntry(
            id = id,
            documentId = "",
            documentType = "",
            action = "",
        )
        return TimelineEntry(
            id = id,
            documentId = root.string("documentId") ?: "",
            documentType = root.string("documentType") ?: "",
            documentNumber = root.string("documentNumber") ?: "",
            partyId = root.string("partyId"),
            action = root.string("action") ?: "",
            grandTotal = root.string("grandTotal") ?: "",
            timestamp = root.string("timestamp") ?: "",
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
