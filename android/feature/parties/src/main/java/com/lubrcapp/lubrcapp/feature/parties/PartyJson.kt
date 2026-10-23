package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 14.1 — codec between [Party] and the JSON payload of a "customers"
 * operational record.
 *
 * Decode is deliberately tolerant, because the table predates the typed
 * model: Phase 12 wrote free-form payloads (typically just uuid + name,
 * later name + description from the generic form), and one bad record
 * must never take down the whole party list. Rules:
 *
 * - Unknown or missing fields default; unparseable payloads decode to a
 *   minimal party carrying only the record id.
 * - The record id is authoritative: a "uuid" key inside legacy payloads
 *   is ignored, never trusted over the store's id.
 * - Legacy "description" maps to [Party.notes]; a legacy flat
 *   "address"/"city"/"country" triple folds into one billing address.
 * - Numbers are accepted as JSON numbers or as strings (older writers
 *   were not consistent); amounts round-trip as plain strings so
 *   [BigDecimal] precision survives exactly.
 *
 * Encode writes the full typed field set, omitting nulls, so any record
 * saved through the typed model re-encodes cleanly.
 */
object PartyJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(party: Party): String {
        val obj = buildJsonObject {
            put("name", party.name)
            put("type", party.type.name)
            party.businessName?.let { put("businessName", it) }
            party.contactPerson?.let { put("contactPerson", it) }
            party.mobile?.let { put("mobile", it) }
            party.whatsapp?.let { put("whatsapp", it) }
            party.phone?.let { put("phone", it) }
            party.email?.let { put("email", it) }
            if (party.addresses.isNotEmpty()) {
                put(
                    "addresses",
                    buildJsonArray {
                        party.addresses.forEach { address ->
                            add(
                                buildJsonObject {
                                    put("kind", address.kind.name)
                                    address.line1?.let { put("line1", it) }
                                    address.line2?.let { put("line2", it) }
                                    address.city?.let { put("city", it) }
                                    address.region?.let { put("region", it) }
                                    address.postalCode?.let { put("postalCode", it) }
                                    address.country?.let { put("country", it) }
                                },
                            )
                        }
                    },
                )
            }
            party.taxNumber?.let { put("taxNumber", it) }
            party.nationalId?.let { put("nationalId", it) }
            party.openingBalance?.let { put("openingBalance", it.toPlainString()) }
            party.creditLimit?.let { put("creditLimit", it.toPlainString()) }
            party.creditDays?.let { put("creditDays", it) }
            party.paymentTerms?.let { put("paymentTerms", it) }
            party.priceLevel?.let { put("priceLevel", it) }
            party.groupId?.let { put("groupId", it) }
            party.territory?.let { put("territory", it) }
            party.salesperson?.let { put("salesperson", it) }
            if (party.isBlocked) put("isBlocked", true)
            party.notes?.let { put("notes", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): Party {
        val root = parseObject(payload) ?: return Party(id = id, name = "")
        val addresses = decodeAddresses(root)
        return Party(
            id = id,
            name = root.string("name") ?: "",
            type = decodeType(root.string("type")),
            businessName = root.string("businessName"),
            contactPerson = root.string("contactPerson"),
            mobile = root.string("mobile"),
            whatsapp = root.string("whatsapp"),
            phone = root.string("phone"),
            email = root.string("email"),
            addresses = addresses,
            taxNumber = root.string("taxNumber"),
            nationalId = root.string("nationalId"),
            openingBalance = root.decimal("openingBalance"),
            creditLimit = root.decimal("creditLimit"),
            creditDays = root.int("creditDays"),
            paymentTerms = root.string("paymentTerms"),
            priceLevel = root.string("priceLevel"),
            groupId = root.string("groupId"),
            territory = root.string("territory"),
            salesperson = root.string("salesperson"),
            isBlocked = root.boolean("isBlocked") ?: false,
            notes = root.string("notes") ?: root.string("description"),
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

    private fun decodeType(raw: String?): PartyType {
        if (raw == null) return PartyType.CUSTOMER
        return try {
            PartyType.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            PartyType.CUSTOMER
        }
    }

    private fun decodeAddresses(root: JsonObject): List<PartyAddress> {
        val array = root["addresses"] as? JsonArray
        if (array != null) {
            return array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                PartyAddress(
                    kind = decodeKind(obj.string("kind")),
                    line1 = obj.string("line1"),
                    line2 = obj.string("line2"),
                    city = obj.string("city"),
                    region = obj.string("region"),
                    postalCode = obj.string("postalCode"),
                    country = obj.string("country"),
                )
            }
        }
        // Legacy flat address triple folds into a single billing address.
        val line1 = root.string("address")
        val city = root.string("city")
        val country = root.string("country")
        if (line1 == null && city == null && country == null) return emptyList()
        return listOf(
            PartyAddress(
                kind = PartyAddress.Kind.BILLING,
                line1 = line1,
                city = city,
                country = country,
            ),
        )
    }

    private fun decodeKind(raw: String?): PartyAddress.Kind {
        if (raw == null) return PartyAddress.Kind.BILLING
        return try {
            PartyAddress.Kind.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            PartyAddress.Kind.BILLING
        }
    }

    /** The primitive at [key], or null when absent or an explicit JSON null. */
    private fun JsonObject.primitive(key: String): JsonPrimitive? {
        val element = this[key] ?: return null
        if (element is kotlinx.serialization.json.JsonNull) return null
        return element as? JsonPrimitive
    }

    private fun JsonObject.string(key: String): String? {
        val primitive = primitive(key) ?: return null
        // Tolerate numbers/booleans written where a string is expected.
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

    private fun JsonObject.int(key: String): Int? {
        val primitive = primitive(key) ?: return null
        return primitive.content.toIntOrNull()
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = primitive(key) ?: return null
        if (!primitive.isString) return primitive.content.toBoolean()
        return primitive.content.toBooleanStrictOrNull()
    }
}
