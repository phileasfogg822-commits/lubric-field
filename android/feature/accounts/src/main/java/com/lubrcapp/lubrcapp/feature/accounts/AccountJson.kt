package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 20.3 — codec between [AccountRecord] and the JSON
 * payload of an "accounts" operational record. Same tolerance
 * contract as the Phase 14–20.2 codecs: unknown/missing
 * fields default, unparseable payloads decode to a minimal
 * record carrying only the record id, and the record id
 * always wins over any in-payload id.
 *
 * Two documented migrations: a missing or unknown type
 * decodes as OTHER_INCOME (the catch-all §74 class), and a
 * missing name falls back to the legacy Phase 12
 * "description" field, so pre-20.3 rows keep displaying.
 */
object AccountJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: AccountRecord): String {
        val obj = buildJsonObject {
            put("code", record.code)
            put("name", record.name)
            put("type", record.type.name)
            record.parentId?.let { put("parentId", it) }
            put("isActive", record.isActive)
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): AccountRecord {
        val root = parseObject(payload)
            ?: return AccountRecord(id = id)
        return AccountRecord(
            id = id,
            code = root.string("code") ?: "",
            name = root.string("name") ?: root.string("description") ?: "",
            type = root.string("type")?.let { name ->
                runCatching { AccountType.valueOf(name) }.getOrNull()
            } ?: AccountType.OTHER_INCOME,
            parentId = root.string("parentId"),
            isActive = root.boolean("isActive") ?: true,
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

    private fun JsonObject.boolean(key: String): Boolean? {
        val element = get(key) ?: return null
        return try {
            element.jsonPrimitive.content.toBooleanStrictOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
