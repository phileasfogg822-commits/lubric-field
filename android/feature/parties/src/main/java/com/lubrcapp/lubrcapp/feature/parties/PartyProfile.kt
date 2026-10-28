package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Phase 14.4 — the customer profile header (master spec §17) as a
 * read-only detail surface: name, type, group, the opening-balance
 * line, and the §18 credit facts with their warning state.
 *
 * §17's tabs (Transactions / Orders / Payments / Items / Statement)
 * and actions (Invoice / Order / Payment / Statement) are NOT here —
 * they need the sales and ledger phases (17–22) and showing dead
 * buttons would fake capability. What exists today is shown; Edit
 * jumps into the §16 form.
 *
 * Every balance figure is the opening balance — the only balance
 * fact until the Phase 22 ledger — and the credit assessment says
 * so by construction ([PartyCredit]).
 */
@Composable
fun PartyProfileMode(
    party: Party,
    groupName: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    ledgerTabs: PartyLedgerTabsData? = null,
) {
    val assessment = PartyCredit.assess(party)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.parties_back)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PartyAvatar(photoUri = party.photoUri, name = party.name, size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(party.name, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(party.type.labelRes()), style = MaterialTheme.typography.titleMedium)
            }
        }
        party.groupId?.let {
            Text(stringResource(R.string.parties_group_line, groupName ?: "—"))
        }
        party.openingBalanceDirection()?.let { direction ->
            Text(
                stringResource(
                    when (direction) {
                        OpeningBalanceDirection.RECEIVABLE -> R.string.parties_receivable
                        OpeningBalanceDirection.PAYABLE -> R.string.parties_payable
                        OpeningBalanceDirection.NEUTRAL -> R.string.parties_opening
                    },
                    party.openingBalance!!.toPlainString(),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        // Phase 32.2 — the live balance header: the statement's last
        // running balance, in the same direction language as the list.
        // The opening line above stays; this is the current position.
        ledgerTabs?.statement?.lastOrNull()?.let { last ->
            val current = last.runningBalanceText.toBigDecimalOrNull()
            if (current != null && current.signum() != 0) {
                Text(
                    stringResource(
                        if (current.signum() > 0) R.string.parties_current_receivable
                        else R.string.parties_current_payable,
                        current.abs().toPlainString(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Text(stringResource(R.string.parties_credit), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.parties_outstanding, assessment.outstanding.toPlainString()))
        if (assessment.creditLimit != null) {
            Text(stringResource(R.string.parties_credit_limit_line, assessment.creditLimit.toPlainString()))
        } else {
            Text(stringResource(R.string.parties_credit_none))
        }
        party.creditDays?.let {
            Text(stringResource(R.string.parties_credit_days_line, it))
        }
        Text(
            stringResource(
                when (assessment.status) {
                    PartyCredit.Status.BLOCKED -> R.string.parties_blocked
                    PartyCredit.Status.OVER_LIMIT -> R.string.parties_status_over
                    PartyCredit.Status.NEAR_LIMIT -> R.string.parties_status_near
                    PartyCredit.Status.WITHIN_LIMIT -> R.string.parties_status_within
                    PartyCredit.Status.NO_LIMIT -> R.string.parties_status_no_limit
                },
            ),
            color = when (assessment.status) {
                PartyCredit.Status.BLOCKED, PartyCredit.Status.OVER_LIMIT ->
                    MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        party.paymentTerms?.let { Text(stringResource(R.string.parties_payment_terms_line, it)) }
        party.mobile?.let { Text(stringResource(R.string.parties_mobile_line, it)) }
        party.email?.let { Text(stringResource(R.string.parties_email_line, it)) }
        party.notes?.let { Text(stringResource(R.string.parties_notes_line, it)) }
        Button(onClick = onEdit) { Text(stringResource(R.string.parties_edit, party.name)) }
        ledgerTabs?.let { tabs ->
            var selectedTab by remember { mutableStateOf(PartyProfileTab.TRANSACTIONS) }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabButton(stringResource(R.string.parties_tab_transactions), selectedTab == PartyProfileTab.TRANSACTIONS) { selectedTab = PartyProfileTab.TRANSACTIONS }
                TabButton(stringResource(R.string.parties_tab_statement), selectedTab == PartyProfileTab.STATEMENT) { selectedTab = PartyProfileTab.STATEMENT }
                TabButton(stringResource(R.string.parties_tab_payments), selectedTab == PartyProfileTab.PAYMENTS) { selectedTab = PartyProfileTab.PAYMENTS }
                TabButton(stringResource(R.string.parties_tab_orders), selectedTab == PartyProfileTab.ORDERS) { selectedTab = PartyProfileTab.ORDERS }
                TabButton(stringResource(R.string.parties_tab_items), selectedTab == PartyProfileTab.ITEMS) { selectedTab = PartyProfileTab.ITEMS }
            }
            when (selectedTab) {
                PartyProfileTab.TRANSACTIONS -> {
                    if (tabs.transactions.isEmpty()) {
                        Text(stringResource(R.string.parties_ledger_empty))
                    } else {
                        tabs.transactions.forEach { row ->
                            Text(stringResource(R.string.parties_ledger_row, row.date, row.documentNumber, row.amountText))
                        }
                    }
                }
                PartyProfileTab.STATEMENT -> {
                    if (tabs.statement.isEmpty()) {
                        Text(stringResource(R.string.parties_ledger_empty))
                    } else {
                        tabs.statement.forEach { row ->
                            Text(stringResource(R.string.parties_ledger_row_statement, row.date, row.documentNumber, row.amountText, row.runningBalanceText))
                        }
                    }
                }
                PartyProfileTab.PAYMENTS -> {
                    if (tabs.payments.isEmpty()) {
                        Text(stringResource(R.string.parties_ledger_empty))
                    } else {
                        tabs.payments.forEach { row ->
                            Text(stringResource(R.string.parties_ledger_row, row.date, row.documentNumber, row.amountText))
                        }
                    }
                }
                PartyProfileTab.ORDERS -> {
                    if (tabs.orders.isEmpty()) {
                        Text(stringResource(R.string.parties_ledger_empty))
                    } else {
                        tabs.orders.forEach { row ->
                            Text(stringResource(R.string.parties_ledger_order_row, row.number, row.date, row.status, row.totalText))
                        }
                    }
                }
                PartyProfileTab.ITEMS -> {
                    if (tabs.items.isEmpty()) {
                        Text(stringResource(R.string.parties_ledger_empty))
                    } else {
                        tabs.items.forEach { row ->
                            Text(stringResource(R.string.parties_ledger_item_row, row.name, row.quantityText, row.totalText))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
