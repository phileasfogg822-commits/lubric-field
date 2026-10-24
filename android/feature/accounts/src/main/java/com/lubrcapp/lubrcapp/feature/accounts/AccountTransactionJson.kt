package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 20.2 — codec between [AccountTransactionRecord] and
 * the JSON payload of an "account_transactions" operational
 * record. Same tolerance contract as the Phase 14–20.1
 * codecs: unknown/missing fields default, unparseable
 * payloads decode to a minimal record carrying only the
 * record id, and the record id always wins over any
 * in-payload id. An unparseable amount decodes as ZERO.
 * Amounts serialize as plain strings so a row round-trips
 * exactly.
 */
object AccountTransactionJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: AccountTransactionRecord): String {
        val obj = buildJsonObject {
            put("entryId", record.entryId)
            put("accountId", record.accountId)
            put("debit", record.debit.toPlainString())
            put("credit", record.credit.toPlainString())
            put("date", record.date)
            put("description", record.description)
            record.referenceNumber?.let { put("referenceNumber", it) }
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): AccountTransactionRecord {
        val root = parseObject(payload)
            ?: return AccountTransactionRecord(id = id)
        return AccountTransactionRecord(
            id = id,
            entryId = root.string("entryId") ?: "",
            accountId = root.string("accountId") ?: "",
            debit = root.amount("debit"),
            credit = root.amount("credit"),
            date = root.string("date") ?: "",
            description = root.string("description") ?: "",
            referenceNumber = root.string("referenceNumber"),
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

    private fun JsonObject.amount(key: String): BigDecimal {
        val raw = string(key) ?: return BigDecimal.ZERO
        return try {
            BigDecimal(raw)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }
}
