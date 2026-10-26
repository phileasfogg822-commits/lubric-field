package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.Money

/**
 * Phase 26.4 — cash drawer (§151): Opening Cash on open,
 * Expected / Actual / Difference on close. Pure session
 * math only: no posting, no persistence — the drawer
 * session is a counter-shift record, and any posting of
 * its movements rides the 22.x cash-movement machinery
 * when the shift closes (26.5).
 *
 * Expected cash is opening cash plus cash in, minus cash
 * out. Movements carry an optional note (e.g. "sale",
 * "refund", "expense") for the shift summary, but 26.4
 * does not categorize them — kinds are a 26.5 concern.
 * Difference is actual minus expected: positive means
 * over, negative means short. All amounts share one
 * currency; mixing throws on Money.plus, as elsewhere.
 */
enum class DrawerMovementDirection {
    IN,
    OUT,
}

data class DrawerMovement(
    val amount: Money,
    val direction: DrawerMovementDirection,
    val note: String = "",
)

data class DrawerSession(
    val id: String,
    val openingCash: Money,
    val movements: List<DrawerMovement> = emptyList(),
)

data class DrawerClose(
    val expected: Money,
    val actual: Money,
    val difference: Money,
)

object CashDrawer {

    fun openSession(id: String, openingCash: Money): DrawerSession =
        DrawerSession(id = id, openingCash = openingCash)

    fun recordMovement(session: DrawerSession, movement: DrawerMovement): DrawerSession =
        session.copy(movements = session.movements + movement)

    fun expectedCash(session: DrawerSession): Money =
        session.movements.fold(session.openingCash) { acc, movement ->
            when (movement.direction) {
                DrawerMovementDirection.IN -> acc + movement.amount
                DrawerMovementDirection.OUT -> acc - movement.amount
            }
        }

    fun closeSession(session: DrawerSession, actualCash: Money): DrawerClose {
        val expected = expectedCash(session)
        return DrawerClose(
            expected = expected,
            actual = actualCash,
            difference = actualCash - expected,
        )
    }
}
