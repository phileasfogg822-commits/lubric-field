package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    val assessment = PartyCredit.assess(party)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.parties_back)) }
        Text(party.name, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(party.type.labelRes()), style = MaterialTheme.typography.titleMedium)
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
    }
}
