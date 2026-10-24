package com.lubrcapp.lubrcapp.feature.inventory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 19.6 — one §30 serial number registered against an item
 * (electronics, batteries, machines: the units whose identity
 * matters, not just their count). Stored as a "serial_numbers"
 * record — NOT in the Phase 12 sync whitelist → local-only, the
 * same posture as transfer headers and stock levels: the
 * registry is device-side working data, while stock truth lives
 * in the movements, which do sync.
 *
 * A serial is never deleted: it leaves the shelf by status
 * (IN_STOCK → SOLD when a sale takes the exact serial, → REMOVED
 * when it is written off here), so the registry stays an audit
 * trail. Sale-time removal itself lands with Phase 21 document
 * posting; this phase provides the registry, the receive/adjust
 * flows, and the status surface Phase 21 will call.
 */
enum class SerialStatus {
    IN_STOCK,
    SOLD,
    REMOVED,
}

data class StockSerialRecord(
    val id: String,
    val itemId: String,
    val serialNumber: String,
    val status: SerialStatus = SerialStatus.IN_STOCK,
    val warehouseId: String? = null,
    /** ISO date text as entered (yyyy-MM-dd). */
    val date: String = "",
    /** Why the serial left stock, for REMOVED records (§36-style reason). */
    val note: String = "",
    /** ISO instant the serial was recorded. */
    val createdAt: String = "",
)

/** The outcome of registering a serial from the receive flow. */
sealed interface SerialAddResult {
    data class Added(val record: StockSerialRecord) : SerialAddResult
    /** The same item already holds this serial IN_STOCK. */
    data object Duplicate : SerialAddResult
    /** Blank serial number — nothing was recorded. */
    data object Invalid : SerialAddResult
}

/**
 * Codec between [StockSerialRecord] and the JSON payload of a
 * "serial_numbers" record. Same tolerance contract as the other
 * Phase 19 codecs: unknown/missing fields default, an unknown
 * status decodes as IN_STOCK (the least-committed state), and
 * the record id always wins over any in-payload id.
 */
object StockSerialJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: StockSerialRecord): String {
        val obj = buildJsonObject {
            put("itemId", record.itemId)
            put("serialNumber", record.serialNumber)
            put("status", record.status.name)
            record.warehouseId?.let { put("warehouseId", it) }
            put("date", record.date)
            put("note", record.note)
            put("createdAt", record.createdAt)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(id: String, payload: String): StockSerialRecord {
        val root = parseObject(payload)
            ?: return StockSerialRecord(id = id, itemId = "", serialNumber = "")
        return StockSerialRecord(
            id = id,
            itemId = root.string("itemId") ?: "",
            serialNumber = root.string("serialNumber") ?: "",
            status = root.string("status")?.let { name ->
                SerialStatus.entries.firstOrNull { it.name == name }
            } ?: SerialStatus.IN_STOCK,
            warehouseId = root.string("warehouseId"),
            date = root.string("date") ?: "",
            note = root.string("note") ?: "",
            createdAt = root.string("createdAt") ?: "",
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

/** The outcome of the 21.3 sale-time SOLD flip. */
data class FlipSoldResult(
    /** The full registry with flipped records replaced. */
    val updatedSerials: List<StockSerialRecord>,
    /** The records flipped IN_STOCK → SOLD by this call. */
    val flipped: List<StockSerialRecord>,
    /** Requested numbers that were not flipped: unknown, wrong item, or no longer in stock. */
    val notFlippedNumbers: List<String>,
)

/**
 * Phase 21.3 — the sale-time SOLD flip 19.6 deferred: flip
 * IN_STOCK → SOLD for the named serial numbers of one item.
 * Matching is by (itemId, serialNumber) — the number alone is
 * not unique across items — and each distinct requested number
 * flips at most one record (the (item, number, IN_STOCK)
 * combination is unique by the 19.6 Duplicate rule; the guard
 * is belt-and-braces). Already-SOLD/REMOVED records and
 * unknown numbers land in [FlipSoldResult.notFlippedNumbers]
 * rather than failing the call, so the caller can report them.
 * The note is left untouched: it records why a serial was
 * REMOVED, and a sale needs no reason text. The sale itself is
 * evidenced by the invoice line's serial numbers plus the
 * posted stock movement — no invoice link is stored here.
 */
fun flipSerialsToSold(
    serials: List<StockSerialRecord>,
    itemId: String,
    serialNumbers: List<String>,
): FlipSoldResult {
    val distinct = serialNumbers.distinct()
    val flipped = mutableListOf<StockSerialRecord>()
    val flippedNumbers = mutableSetOf<String>()
    val updated = serials.map { record ->
        if (record.itemId == itemId && record.status == SerialStatus.IN_STOCK &&
            record.serialNumber in distinct && record.serialNumber !in flippedNumbers
        ) {
            flippedNumbers.add(record.serialNumber)
            record.copy(status = SerialStatus.SOLD).also { flipped.add(it) }
        } else {
            record
        }
    }
    return FlipSoldResult(
        updatedSerials = updated,
        flipped = flipped,
        notFlippedNumbers = distinct.filter { it !in flippedNumbers },
    )
}

/** The outcome of the 21.4 return flip. */
data class FlipStockResult(
    /** The full registry with flipped records replaced. */
    val updatedSerials: List<StockSerialRecord>,
    /** The records flipped SOLD → IN_STOCK by this call. */
    val flipped: List<StockSerialRecord>,
    /** Requested numbers that were not flipped: unknown, wrong item, or not sold. */
    val notFlippedNumbers: List<String>,
)

/**
 * Phase 21.4 — the inverse of 21.3's sale flip: returned
 * goods go back to stock. Flips SOLD → IN_STOCK for the named
 * serial numbers of one item, one record per distinct number.
 * IN_STOCK records (never sold), REMOVED records (written off
 * for a reason — a damaged return goes through removeSerial,
 * not here), unknown numbers, and other items' numbers land
 * in [FlipStockResult.notFlippedNumbers] rather than failing
 * the call. The note is left untouched, the 21.3 pattern.
 */
fun flipSerialsToStock(
    serials: List<StockSerialRecord>,
    itemId: String,
    serialNumbers: List<String>,
): FlipStockResult {
    val distinct = serialNumbers.distinct()
    val flipped = mutableListOf<StockSerialRecord>()
    val flippedNumbers = mutableSetOf<String>()
    val updated = serials.map { record ->
        if (record.itemId == itemId && record.status == SerialStatus.SOLD &&
            record.serialNumber in distinct && record.serialNumber !in flippedNumbers
        ) {
            flippedNumbers.add(record.serialNumber)
            record.copy(status = SerialStatus.IN_STOCK).also { flipped.add(it) }
        } else {
            record
        }
    }
    return FlipStockResult(
        updatedSerials = updated,
        flipped = flipped,
        notFlippedNumbers = distinct.filter { it !in flippedNumbers },
    )
}
