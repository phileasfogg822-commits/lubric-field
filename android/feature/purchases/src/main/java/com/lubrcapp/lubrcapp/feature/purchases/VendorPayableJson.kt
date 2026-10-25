package com.lubrcapp.lubrcapp.feature.purchases

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 23.1 — codec between [VendorPayableRecord] and
 * the JSON payload of a "vendor_payables" operational
 * record. Same tolerance contract as the Phase 14–22.3
 * codecs: unknown/missing fields default, unparseable
 * payloads decode to a minimal record carrying only the
 * record id, and the record id always wins over any
 * in-payload id. An unparseable amount decodes as ZERO.
 * Amounts serialize as plain strings so a record
 * round-trips exactly. Status round-trips as stored
 * text; the codec never normalizes it.
 */
object VendorPayableJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: VendorPayableRecord): String {
        val obj = buildJsonObject {
            put("vendorId", record.vendorId)
            put("documentNumber", record.documentNumber)
            put("status", record.status)
            put("totalAmount", record.totalAmount.toPlainString())
            put("paidAmount", record.paidAmount.toPlainString())
            put("currency", record.currency)
            put("date", record.date)
            put("dueDate", record.dueDate)
            put("notes", record.notes)
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): VendorPayableRecord {
        val root = parseObject(payload)
            ?: return VendorPayableRecord(id = id)
        return VendorPayableRecord(
            id = id,
            vendorId = root.string("vendorId") ?: "",
            documentNumber = root.string("documentNumber") ?: "",
            status = root.string("status") ?: "DRAFT",
            totalAmount = root.amount("totalAmount"),
            paidAmount = root.amount("paidAmount"),
            currency = root.string("currency") ?: "",
            date = root.string("date") ?: "",
            dueDate = root.string("dueDate") ?: "",
            notes = root.string("notes") ?: "",
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
