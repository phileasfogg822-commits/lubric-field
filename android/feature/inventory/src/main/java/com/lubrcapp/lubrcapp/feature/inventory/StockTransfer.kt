package com.lubrcapp.lubrcapp.feature.inventory

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 19.5 — a §35 stock transfer: stock moving between two
 * warehouses of the same business. The header is stored as a
 * "stock_transfers" record with its lines embedded — that table
 * is NOT in the Phase 12 sync whitelist → local-only, the
 * numbering / timeline posture from Phase 18: the header is
 * coordination state, while the stock truth is the paired
 * movements it posts (TRANSFER_OUT at the source on dispatch,
 * TRANSFER_IN at the destination on receive), and movements DO
 * sync. One record for header + lines keeps a status transition
 * atomic: there is no half-saved transfer.
 *
 * Status flow (§35): DRAFT → DISPATCHED → RECEIVED, with
 * CANCELLED reachable only from DRAFT — a dispatched transfer
 * has already posted its outbound movements, so it must be
 * received, never cancelled out from under the ledger.
 */
enum class TransferStatus {
    DRAFT,
    DISPATCHED,
    RECEIVED,
    CANCELLED,
}

/** One line of a transfer: an item and the unsigned quantity to move. */
data class StockTransferLine(
    val itemId: String,
    val quantity: BigDecimal,
)

data class StockTransferRecord(
    val id: String,
    val fromWarehouseId: String,
    val toWarehouseId: String,
    val status: TransferStatus = TransferStatus.DRAFT,
    /** ISO date text as entered (yyyy-MM-dd). */
    val date: String = "",
    val note: String = "",
    val lines: List<StockTransferLine> = emptyList(),
    /** ISO instant the transfer was recorded. */
    val createdAt: String = "",
)

/**
 * The §35 editor state: quantities are entered per item (the
 * stock-count pattern), so a transfer can never carry two lines
 * for the same item — dispatch checks and posts per item.
 */
data class StockTransferFormState(
    val fromWarehouseId: String? = null,
    val toWarehouseId: String? = null,
    val date: String = "",
    val note: String = "",
    val quantityByItem: Map<String, String> = emptyMap(),
) {
    /** The lines that would be saved: items with a positive parsed quantity. */
    val validLines: List<StockTransferLine>
        get() = quantityByItem.entries.mapNotNull { (itemId, text) ->
            val quantity = text.trim().takeIf { it.isNotEmpty() }?.let {
                try {
                    BigDecimal(it)
                } catch (e: Exception) {
                    null
                }
            }
            if (quantity != null && quantity.signum() > 0) {
                StockTransferLine(itemId = itemId, quantity = quantity)
            } else {
                null
            }
        }

    val isValid: Boolean
        get() = fromWarehouseId != null &&
            toWarehouseId != null &&
            fromWarehouseId != toWarehouseId &&
            validLines.isNotEmpty()
}

/** The outcome of a transfer status action (dispatch / receive / cancel). */
sealed interface TransferResult {
    data class Done(val transfer: StockTransferRecord, val warning: Boolean = false) : TransferResult
    data object BlockedByPolicy : TransferResult
    data object InvalidState : TransferResult
}

/**
 * Codec between [StockTransferRecord] and the JSON payload of a
 * "stock_transfers" record. Same tolerance contract as the other
 * Phase 19 codecs: unknown/missing fields default, an unknown
 * status decodes as DRAFT (the least-committed state — it has
 * posted nothing), a line with an unparseable quantity keeps its
 * item with quantity ZERO (dispatch skips non-positive lines;
 * the codec never guesses a quantity), and the record id always
 * wins over any in-payload id. Amounts serialize as plain
 * strings so a transfer round-trips exactly.
 */
object StockTransferJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: StockTransferRecord): String {
        val obj = buildJsonObject {
            put("fromWarehouseId", record.fromWarehouseId)
            put("toWarehouseId", record.toWarehouseId)
            put("status", record.status.name)
            put("date", record.date)
            put("note", record.note)
            put(
                "lines",
                buildJsonArray {
                    record.lines.forEach { line ->
                        add(
                            buildJsonObject {
                                put("itemId", line.itemId)
                                put("quantity", line.quantity.toPlainString())
                            }
                        )
                    }
                },
            )
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): StockTransferRecord {
        val root = parseObject(payload)
            ?: return StockTransferRecord(id = id, fromWarehouseId = "", toWarehouseId = "")
        return StockTransferRecord(
            id = id,
            fromWarehouseId = root.string("fromWarehouseId") ?: "",
            toWarehouseId = root.string("toWarehouseId") ?: "",
            status = root.string("status")?.let { name ->
                TransferStatus.entries.firstOrNull { it.name == name }
            } ?: TransferStatus.DRAFT,
            date = root.string("date") ?: "",
            note = root.string("note") ?: "",
            lines = root.lines(),
            createdAt = root.string("createdAt") ?: "",
        )
    }

    private fun JsonObject.lines(): List<StockTransferLine> {
        val array = this["lines"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val itemId = obj.string("itemId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StockTransferLine(
                itemId = itemId,
                quantity = obj.decimal("quantity") ?: BigDecimal.ZERO,
            )
        }
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
        val content = string(key) ?: return null
        return try {
            BigDecimal(content)
        } catch (e: Exception) {
            null
        }
    }
}
