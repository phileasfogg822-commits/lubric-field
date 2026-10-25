package com.lubrcapp.lubrcapp.feature.parties

/**
 * Phase 22.5 — plain tab data for the §17 customer
 * profile's ledger tabs. These are UI data, not ledger
 * types: feature/parties takes NO dependency on
 * feature/accounts (no feature module depends on another
 * feature — the app layer maps the built
 * PartyLedgerProfile view into these rows, the same way
 * SalesScreen takes account options as plain params).
 * Amounts arrive pre-formatted as text; formatting Money
 * is the caller's job, not the screen's.
 */
data class PartyLedgerRow(
    val date: String,
    val documentNumber: String,
    val description: String,
    val amountText: String,
)

data class PartyStatementRow(
    val date: String,
    val documentNumber: String,
    val description: String,
    val amountText: String,
    val runningBalanceText: String,
)

data class PartyOrderRow(
    val number: String,
    val date: String,
    val status: String,
    val totalText: String,
)

data class PartyItemRow(
    val name: String,
    val quantityText: String,
    val totalText: String,
)

enum class PartyProfileTab {
    TRANSACTIONS,
    STATEMENT,
    PAYMENTS,
    ORDERS,
    ITEMS,
}

/**
 * The whole tab bundle, or null when the caller has no
 * ledger data wired yet. Null preserves the 14.4
 * header-only profile exactly (no dead tabs); non-null
 * renders the tabs even when every list is empty — a
 * wired party with no transactions yet is a real empty
 * state, not a missing capability.
 */
data class PartyLedgerTabsData(
    val transactions: List<PartyLedgerRow> = emptyList(),
    val statement: List<PartyStatementRow> = emptyList(),
    val payments: List<PartyLedgerRow> = emptyList(),
    val orders: List<PartyOrderRow> = emptyList(),
    val items: List<PartyItemRow> = emptyList(),
)
