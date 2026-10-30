package com.lubrcapp.lubrcapp.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.FormSection
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Phase 20.4 — typed accounts screen: chart of accounts + journal with trial-balance preview. */
private enum class AccountsMode { CHART, JOURNAL, CASH }

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
            Button(onClick = { mode = AccountsMode.CASH; selectedEntry = null }) {
                Text(stringResource(R.string.accounts_mode_cash))
            }
        }
        // weight(1f), NOT fillMaxSize, for the scrolling content: a
        // fillMaxSize child inside a Column takes the whole Column height
        // (not the remaining height), so its tail would extend below the
        // window and could never be scrolled into view — the below-fold
        // rows the 20.4 tests assert on. The weighted Box gives the inner
        // scrolling Column exactly the remaining height, so every row is
        // reachable by scroll.
        Box(modifier = Modifier.weight(1f)) {
            when (mode) {
                AccountsMode.CHART -> ChartContent(scope, accounts, viewModel)
                AccountsMode.JOURNAL -> JournalContent(
                    entries = entries,
                    accounts = accounts,
                    selectedEntry = selectedEntry,
                    onSelectEntry = { selectedEntry = it },
                    onBackToList = { selectedEntry = null },
                )
                AccountsMode.CASH -> CashContent(
                    scope = scope,
                    accounts = accounts,
                    entries = entries,
                    viewModel = viewModel,
                )
            }
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
        FormSection(title = null) {
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
        }
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.accounts_empty))
        }
        accounts.forEach { account ->
            val rowText = if (account.isActive) {
                stringResource(R.string.accounts_account_row, account.code, account.name, account.type.name)
            } else {
                stringResource(R.string.accounts_account_row_inactive, account.code, account.name, account.type.name)
            }
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
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
    }
}

/**
 * Phase 31.3 — the general cash book. The user picks which
 * account is their cash account (AccountType has no CASH
 * value, so no type filter — every active account is offered)
 * and a counterpart, a direction, an amount and a description,
 * then records the entry straight through
 * [AccountsViewModel.postDocument]: the accounts feature owns
 * posting, so no shell handler is needed. The selection is
 * remembered per scope, not persisted. Below the form, the
 * selected cash account's entries and its running balance
 * come from [CashBook] over the observed journal entries, so
 * a just-recorded entry shows up through the same Flow as
 * the journal tab. Currency is the shell "XXX" default —
 * app-wide currency wiring is still deferred.
 */
@Composable
private fun CashContent(
    scope: OperationalScope,
    accounts: List<AccountRecord>,
    entries: List<JournalEntryRecord>,
    viewModel: AccountsViewModel,
) {
    val activeAccounts = accounts.filter { it.isActive }
    var cashId by remember(scope) { mutableStateOf<String?>(null) }
    var counterpartId by remember(scope) { mutableStateOf<String?>(null) }
    var direction by remember(scope) { mutableStateOf(CashDirection.IN) }
    var amount by remember(scope) { mutableStateOf("") }
    var description by remember(scope) { mutableStateOf("") }
    var recording by remember(scope) { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val cashInText = stringResource(R.string.accounts_cash_in)
    val cashOutText = stringResource(R.string.accounts_cash_out)
    val recordedText = stringResource(R.string.accounts_cash_recorded)
    val rejectedText = stringResource(R.string.accounts_cash_rejected)
    val recordErrorText = stringResource(R.string.accounts_cash_error)
    val amountValue = amount.toBigDecimalOrNull()

    // One scrolling Column: every node stays composed, so
    // tests can type into the form and tap rows without the
    // LazyColumn below-fold / zero-height traps (19.4, 19.6).
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.accounts_cash_title), style = MaterialTheme.typography.titleMedium)
        FormSection(title = stringResource(R.string.accounts_cash_account_label)) {
        activeAccounts.forEach { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = cashId == account.id,
                    onClick = { cashId = account.id },
                    role = Role.RadioButton,
                ),
            ) {
                RadioButton(
                    selected = cashId == account.id,
                    onClick = null,
                )
                Text(account.name)
            }
        }
        }
        FormSection(title = stringResource(R.string.accounts_cash_counterpart_label)) {
        activeAccounts.forEach { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = counterpartId == account.id,
                    onClick = { counterpartId == account.id },
                    role = Role.RadioButton,
                ),
            ) {
                RadioButton(
                    selected = counterpartId == account.id,
                    onClick = null,
                )
                Text(account.name)
            }
        }
        }
        FormSection(title = null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { direction = CashDirection.IN }) { Text(cashInText) }
            Button(onClick = { direction = CashDirection.OUT }) { Text(cashOutText) }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(stringResource(R.string.accounts_cash_amount_label)) },
            modifier = Modifier.fillMaxWidth().testTag("cashAmount"),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.accounts_cash_description_label)) },
            modifier = Modifier.fillMaxWidth().testTag("cashDescription"),
        )
        Button(
            enabled = amountValue != null && amountValue > BigDecimal.ZERO &&
                cashId != null && counterpartId != null && !recording,
            onClick = {
                recording = true
                coroutineScope.launch {
                    try {
                        val parsed = amountValue ?: return@launch
                        val cash = cashId ?: return@launch
                        val counterpart = counterpartId ?: return@launch
                        val posting = CashPosting.cashToPosting(
                            Money.of(parsed, CurrencyCode.of("XXX")),
                            direction,
                            CashPostingAccounts(cash, counterpart),
                            "",
                        )
                        val result = viewModel.postDocument(
                            scope = scope,
                            posting = posting,
                            date = LocalDate.now(),
                            description = description.ifBlank {
                                if (direction == CashDirection.IN) cashInText else cashOutText
                            },
                            now = Instant.now(),
                            deviceId = DeviceId(scope.deviceId),
                        )
                        if (result is AccountsViewModel.DocumentPostResult.Posted) {
                            notice = recordedText
                            error = null
                            amount = ""
                            description = ""
                        } else {
                            error = rejectedText
                        }
                    } catch (_: Exception) {
                        error = recordErrorText
                    } finally {
                        recording = false
                    }
                }
            },
            modifier = Modifier.testTag("cashRecordButton"),
        ) { Text(stringResource(R.string.accounts_cash_record)) }
        }
        notice?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val selectedCash = activeAccounts.firstOrNull { it.id == cashId }
        if (selectedCash != null) {
            Text(
                stringResource(
                    R.string.accounts_cash_balance_row,
                    selectedCash.name,
                    CashBook.balance(entries, selectedCash.id).toPlainString(),
                ),
            )
            val cashEntries = CashBook.entriesForAccount(entries, selectedCash.id)
            if (cashEntries.isEmpty()) {
                Text(stringResource(R.string.accounts_cash_entry_empty))
            }
            cashEntries.forEach { entry ->
                val debit = entry.lines.filter { it.accountId == selectedCash.id }
                    .fold(BigDecimal.ZERO) { acc, line -> acc + line.debit }
                val credit = entry.lines.filter { it.accountId == selectedCash.id }
                    .fold(BigDecimal.ZERO) { acc, line -> acc + line.credit }
                Text(
                    stringResource(
                        R.string.accounts_cash_entry_row,
                        entry.date,
                        entry.description,
                        debit.toPlainString(),
                        credit.toPlainString(),
                    ),
                )
            }
        }
    }
}
