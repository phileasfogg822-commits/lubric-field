package com.lubrcapp.lubrcapp.feature.sales

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 18.3 — codec between [NumberingRecord] and the JSON
 * payload of a "numbering_sequences" operational record. Same
 * tolerance contract as the other codecs; the record id (the
 * document-type key) always wins over any in-payload id.
 */
object NumberingRecordJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: NumberingRecord): String {
        val obj = buildJsonObject {
            put("documentType", record.documentType)
            put("prefix", record.prefix)
            put("suffix", record.suffix)
            put("startingNumber", record.startingNumber)
            put("padding", record.padding)
            put("reset", record.reset)
            put("periodKey", record.periodKey)
            put("lastIssued", record.lastIssued)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): NumberingRecord {
        val root = parseObject(payload) ?: return NumberingRecord(id = id, documentType = id, prefix = "")
        return NumberingRecord(
            id = id,
            documentType = root.string("documentType") ?: id,
            prefix = root.string("prefix") ?: "",
            suffix = root.string("suffix") ?: "",
            startingNumber = root.long("startingNumber") ?: 1,
            padding = root.int("padding") ?: 5,
            reset = root.string("reset") ?: "YEARLY",
            periodKey = root.string("periodKey") ?: "",
            lastIssued = root.long("lastIssued") ?: 0,
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

    private fun JsonObject.long(key: String): Long? {
        val primitive = primitive(key) ?: return null
        return primitive.content.toLongOrNull()
    }

    private fun JsonObject.int(key: String): Int? {
        val primitive = primitive(key) ?: return null
        return primitive.content.toIntOrNull()
    }
}
