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
 * Phase 22.3 — codec between [PartyLedgerRecord] and the
 * JSON payload of a "party_ledger" operational record.
 * Same tolerance contract as the Phase 14–20.2 codecs:
 * unknown/missing fields default, unparseable payloads
 * decode to a minimal record carrying only the record id,
 * and the record id always wins over any in-payload id.
 * An unparseable amount decodes as ZERO. Amounts
 * serialize as plain strings so a row round-trips
 * exactly. Direction round-trips as stored text; the
 * codec never normalizes it (balance functions decide
 * what counts).
 */
object PartyLedgerJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: PartyLedgerRecord): String {
        val obj = buildJsonObject {
            put("entryId", record.entryId)
            put("partyId", record.partyId)
            put("direction", record.direction)
            put("amount", record.amount.toPlainString())
            put("currency", record.currency)
            put("transactionId", record.transactionId)
            put("documentType", record.documentType)
            put("documentNumber", record.documentNumber)
            put("date", record.date)
            put("description", record.description)
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): PartyLedgerRecord {
        val root = parseObject(payload)
            ?: return PartyLedgerRecord(id = id)
        return PartyLedgerRecord(
            id = id,
            entryId = root.string("entryId") ?: "",
            partyId = root.string("partyId") ?: "",
            direction = root.string("direction") ?: "RECEIVABLE",
            amount = root.amount("amount"),
            currency = root.string("currency") ?: "",
            transactionId = root.string("transactionId") ?: "",
            documentType = root.string("documentType") ?: "",
            documentNumber = root.string("documentNumber") ?: "",
            date = root.string("date") ?: "",
            description = root.string("description") ?: "",
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
