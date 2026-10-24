package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Phase 20.1 — codec between [JournalEntryRecord] and the JSON
 * payload of a "journal_entries" operational record. Same
 * tolerance contract as the Phase 14–19 codecs: unknown/missing
 * fields default, unparseable payloads decode to a minimal
 * record carrying only the record id, and the record id always
 * wins over any in-payload id. An unparseable amount decodes
 * as ZERO (a zero line changes no balance; the amount is
 * corrected only by a real reversing entry, never guessed
 * here). Amounts serialize as plain strings so an entry
 * round-trips exactly.
 */
object JournalEntryJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: JournalEntryRecord): String {
        val obj = buildJsonObject {
            put("date", record.date)
            put("description", record.description)
            record.referenceNumber?.let { put("referenceNumber", it) }
            putJsonArray("lines") {
                record.lines.forEach { line ->
                    add(buildJsonObject {
                        put("accountId", line.accountId)
                        put("debit", line.debit.toPlainString())
                        put("credit", line.credit.toPlainString())
                        line.memo?.let { put("memo", it) }
                    })
                }
            }
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): JournalEntryRecord {
        val root = parseObject(payload)
            ?: return JournalEntryRecord(id = id)
        return JournalEntryRecord(
            id = id,
            date = root.string("date") ?: "",
            description = root.string("description") ?: "",
            referenceNumber = root.string("referenceNumber"),
            lines = root.array("lines")?.mapNotNull { element ->
                val line = element as? JsonObject ?: return@mapNotNull null
                JournalLineRecord(
                    accountId = line.string("accountId") ?: "",
                    debit = line.amount("debit"),
                    credit = line.amount("credit"),
                    memo = line.string("memo"),
                )
            } ?: emptyList(),
            createdAt = root.string("createdAt") ?: "",
        )
    }

    private fun parseObject(payload: String): JsonObject? = try {
        json.parseToJsonElement(payload).jsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.string(key: String): String? {
        val element = get(key) ?: return null
        if (element is JsonNull) return null
        return try {
            element.jsonPrimitive.content
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.array(key: String): JsonArray? {
        val element = get(key) ?: return null
        return try {
            element.jsonArray
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.amount(key: String): BigDecimal {
        val raw = string(key) ?: return BigDecimal.ZERO
        return try {
            BigDecimal(raw)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }
}
