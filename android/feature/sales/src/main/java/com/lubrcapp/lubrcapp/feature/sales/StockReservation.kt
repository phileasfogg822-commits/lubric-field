package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal

/**
 * Phase 21.2 — §213 stock reservation: a hold on physical stock
 * for one order line, so availability (physical − reserved) can
 * never be promised twice. The header is a "reservations"
 * operational record — that table IS in the Phase 12 sync
 * whitelist (and NOT insert-only, so a release syncs as an
 * update), while "reservations" is NOT in the store's deletable
 * types: a reservation is released by status, never by deletion
 * ("Financial and inventory history requires a reversal, not
 * deletion").
 *
 * The §213 setting — reserve when the order is submitted,
 * when it is confirmed, or never — is caller-supplied
 * ([ReservationPolicy], same posture as the §18 caller-supplied
 * CreditCheck): the ViewModel reserves on the caller's call,
 * and NEVER reserves nothing. The ON_SUBMIT / ON_CONFIRM
 * distinction is the caller's choice of WHEN to call (on save
 * vs on confirm); the record itself carries no policy.
 * Availability math is pure and lives here: the StockLedger
 * (19.2) owns balances, and reserved quantities subtract from
 * them. Availability-aware negative-stock checks are a 21.3/22
 * consumer, not this package.
 */
enum class ReservationStatus {
    ACTIVE,
    RELEASED,
}

/** §213: when stock is reserved for an order. Caller-supplied. */
enum class ReservationPolicy {
    ON_SUBMIT,
    ON_CONFIRM,
    NEVER,
}

data class StockReservationRecord(
    val id: String,
    val orderId: String,
    val lineId: String,
    val itemId: String,
    val warehouseId: String? = null,
    val batchId: String? = null,
    val quantity: BigDecimal,
    val status: ReservationStatus = ReservationStatus.ACTIVE,
    val createdAt: String = "",
)

/** Active reserved quantity for one stock dimension. */
fun activeReservedQuantity(
    reservations: List<StockReservationRecord>,
    itemId: String,
    warehouseId: String? = null,
    batchId: String? = null,
): BigDecimal = reservations
    .filter { it.status == ReservationStatus.ACTIVE }
    .filter { it.itemId == itemId && it.warehouseId == warehouseId && it.batchId == batchId }
    .fold(BigDecimal.ZERO) { sum, reservation -> sum + reservation.quantity }

/** Availability on one dimension: ledger balance minus active reservations. */
fun availableQuantity(balance: BigDecimal, reserved: BigDecimal): BigDecimal = balance - reserved
