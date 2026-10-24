package com.lubrcapp.lubrcapp.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import kotlinx.coroutines.launch

/** Phase 20.4 — typed accounts screen: chart of accounts + journal with trial-balance preview. */
private enum class AccountsMode { CHART, JOURNAL }

@Composable
fun AccountsScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val accounts by remember(viewModel, scope) { viewModel.observeAccounts(scope) }
        .collectAsState(initial = emptyList())
    val entries by remember(viewModel, scope) { viewModel.observeJournalEntries(scope) }
        .collectAsState(initial = emptyList())
    var mode by remember { mutableStateOf(AccountsMode.CHART) }
    var selectedEntry by remember { mutableStateOf<JournalEntryRecord?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.accounts_back)) }
        Text(stringResource(R.string.accounts_title), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mode = AccountsMode.CHART; selectedEntry = null }) {
                Text(stringResource(R.string.accounts_mode_chart))
            }
            Button(onClick = { mode = AccountsMode.JOURNAL; selectedEntry = null }) {
                Text(stringResource(R.string.accounts_mode_journal))
            }
        }
        when (mode) {
            AccountsMode.CHART -> ChartContent(scope, accounts, viewModel)
            AccountsMode.JOURNAL -> JournalContent(
                entries = entries,
                accounts = accounts,
                selectedEntry = selectedEntry,
                onSelectEntry = { selectedEntry = it },
                onBackToList = { selectedEntry = null },
            )
        }
    }
}

@Composable
private fun ChartContent(
    scope: OperationalScope,
    accounts: List<AccountRecord>,
    viewModel: AccountsViewModel,
) {
    var code by remember(scope) { mutableStateOf("") }
    var name by remember(scope) { mutableStateOf("") }
    var selectedType by remember(scope) { mutableStateOf(AccountType.ASSET) }
    var saving by remember(scope) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.accounts_save_error)

    // One scrolling Column: every node stays composed, so
    // tests can type into the form and tap rows without the
    // LazyColumn below-fold / zero-height traps (19.4, 19.6).
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.accounts_code_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.accounts_name_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.accounts_type_label), style = MaterialTheme.typography.titleMedium)
        AccountType.entries.forEach { type ->
            Button(
                onClick = { selectedType = type },
                enabled = !saving,
            ) { Text(type.name) }
        }
        Button(
            enabled = code.isNotBlank() && name.isNotBlank() && !saving,
            onClick = {
                saving = true
                coroutineScope.launch {
                    try {
                        viewModel.saveAccount(
                            scope,
                            AccountRecord.of(code.trim(), name.trim(), selectedType),
                        )
                        code = ""
                        name = ""
                        error = null
                    } catch (_: Exception) {
                        error = saveError
                    } finally {
                        saving = false
                    }
                }
            },
        ) { Text(if (saving) stringResource(R.string.accounts_saving) else stringResource(R.string.accounts_save)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.accounts_empty))
        }
        accounts.forEach { account ->
            val rowText = if (account.isActive) {
                stringResource(R.string.accounts_account_row, account.code, account.name, account.type.name)
            } else {
                stringResource(R.string.accounts_account_row_inactive, account.code, account.name, account.type.name)
            }
            Text(rowText)
            if (account.isActive) {
                Button(
                    enabled = !saving,
                    onClick = {
                        coroutineScope.launch { viewModel.deactivateAccount(scope, account.id) }
                    },
                ) { Text(stringResource(R.string.accounts_deactivate)) }
            }
        }
    }
}

@Composable
private fun JournalContent(
    entries: List<JournalEntryRecord>,
    accounts: List<AccountRecord>,
    selectedEntry: JournalEntryRecord?,
    onSelectEntry: (JournalEntryRecord) -> Unit,
    onBackToList: () -> Unit,
) {
    val balance = trialBalance(entries, accounts)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.accounts_trial_title), style = MaterialTheme.typography.titleMedium)
        balance.rows.forEach { row ->
            Text(
                stringResource(
                    R.string.accounts_trial_row,
                    row.accountName,
                    row.totalDebit.toPlainString(),
                    row.totalCredit.toPlainString(),
                ),
            )
        }
        Text(stringResource(R.string.accounts_trial_debits, balance.totalDebits.toPlainString()))
        Text(stringResource(R.string.accounts_trial_credits, balance.totalCredits.toPlainString()))
        Text(
            if (balance.isBalanced) {
                stringResource(R.string.accounts_trial_balanced)
            } else {
                stringResource(R.string.accounts_trial_out_of_balance)
            },
        )
        val entry = selectedEntry
        if (entry != null) {
            Button(onClick = onBackToList) { Text(stringResource(R.string.accounts_back_to_journal)) }
            Text(entry.description, style = MaterialTheme.typography.titleMedium)
            val names = accounts.associate { it.id to it.name }
            entry.lines.forEach { line ->
                Text(
                    stringResource(
                        R.string.accounts_line_row,
                        names[line.accountId] ?: line.accountId,
                        line.debit.toPlainString(),
                        line.credit.toPlainString(),
                    ),
                )
            }
            Text(stringResource(R.string.accounts_trial_debits, entry.totalDebits.toPlainString()))
            Text(stringResource(R.string.accounts_trial_credits, entry.totalCredits.toPlainString()))
        } else {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.accounts_entry_empty))
            }
            entries.forEach { item ->
                Button(onClick = { onSelectEntry(item) }) {
                    Text(
                        stringResource(
                            R.string.accounts_entry_row,
                            item.date,
                            item.description,
                            item.totalDebits.toPlainString(),
                            item.totalCredits.toPlainString(),
                        ),
                    )
                }
            }
        }
    }
}
