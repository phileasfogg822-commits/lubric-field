package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.Money

/**
 * Phase 26.5 — shifts (§152, optional): Open Shift,
 * Sales, Returns, Cash, Card, Expenses, Close Shift.
 * Pure session math built on the 26.4 drawer: a shift
 * owns one drawer session plus categorized entries, and
 * its summary reads the drawer for cash.
 *
 * Categories are the spec's own summary lines. Cash is
 * NOT an entry kind — it is the drawer's expected cash
 * (opening plus in minus out), so cash can never drift
 * from the drawer count. Card, sales, returns, and
 * expenses are explicit entries because they are not
 * drawer movements. All amounts share the drawer's
 * currency; mixing throws on Money.plus, as elsewhere.
 * No posting, no persistence — postings ride 22.x
 * machinery at shift close (26.7 wiring).
 */
enum class ShiftEntryKind {
    SALES,
    RETURNS,
    CARD,
    EXPENSES,
}

data class ShiftEntry(
    val kind: ShiftEntryKind,
    val amount: Money,
)

data class ShiftSession(
    val id: String,
    val drawer: DrawerSession,
    val entries: List<ShiftEntry> = emptyList(),
)

data class ShiftSummary(
    val sales: Money,
    val returns: Money,
    val cashExpected: Money,
    val card: Money,
    val expenses: Money,
    val cashActual: Money?,
    val cashDifference: Money?,
)

object ShiftOps {

    fun openShift(id: String, openingCash: Money): ShiftSession =
        ShiftSession(id = id, drawer = CashDrawer.openSession(id, openingCash))

    fun recordEntry(session: ShiftSession, entry: ShiftEntry): ShiftSession =
        session.copy(entries = session.entries + entry)

    fun recordDrawerMovement(session: ShiftSession, movement: DrawerMovement): ShiftSession =
        session.copy(drawer = CashDrawer.recordMovement(session.drawer, movement))

    fun summary(session: ShiftSession, cashActual: Money? = null): ShiftSummary {
        val currency = session.drawer.openingCash.currency
        fun total(kind: ShiftEntryKind): Money =
            session.entries.filter { it.kind == kind }
                .fold(Money.zero(currency)) { acc, entry -> acc + entry.amount }
        val cashExpected = CashDrawer.expectedCash(session.drawer)
        return ShiftSummary(
            sales = total(ShiftEntryKind.SALES),
            returns = total(ShiftEntryKind.RETURNS),
            cashExpected = cashExpected,
            card = total(ShiftEntryKind.CARD),
            expenses = total(ShiftEntryKind.EXPENSES),
            cashActual = cashActual,
            cashDifference = cashActual?.let { it - cashExpected },
        )
    }

    fun closeShift(session: ShiftSession, cashActual: Money): ShiftSummary =
        summary(session, cashActual)
}
