package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Phase 14.3 — customer-group management (§19): list with member
 * counts, the group form, and the same tombstone delete flow as
 * parties. Deleting a group never edits its parties — their groupId
 * link stays and renders unresolved ("—") in the party list.
 */
@Composable
fun PartyGroupsMode(
    scope: OperationalScope,
    parties: List<Party>,
    groups: List<PartyGroup>,
    viewModel: PartiesViewModel,
    onBack: () -> Unit,
) {
    val form = remember(scope) { PartyGroupFormState() }
    var formOpen by remember(scope) { mutableStateOf(false) }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.parties_group_error)
    val deleteError = stringResource(R.string.parties_group_delete_error)

    if (formOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingId != null) R.string.parties_edit_group else R.string.parties_new_group),
                style = MaterialTheme.typography.headlineMedium,
            )
            GroupTextField(form.name, { form.name = it }, R.string.parties_group_name)
            GroupTextField(form.priceLevel, { form.priceLevel = it }, R.string.parties_price_level)
            GroupTextField(form.discountPercent, { form.discountPercent = it }, R.string.parties_group_discount, KeyboardType.Decimal)
            GroupTextField(form.paymentTerms, { form.paymentTerms = it }, R.string.parties_payment_terms)
            GroupTextField(form.taxRule, { form.taxRule = it }, R.string.parties_group_tax_rule)
            GroupTextField(form.salesperson, { form.salesperson = it }, R.string.parties_salesperson)
            GroupTextField(form.territory, { form.territory = it }, R.string.parties_territory)
            if (formError) {
                Text(stringResource(R.string.parties_group_form_error), color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = form.name.isNotBlank() && !saving,
                onClick = {
                    val group = form.toGroup(editingId ?: UUID.randomUUID().toString())
                    if (group == null) {
                        formError = true
                    } else {
                        formError = false
                        saving = true
                        coroutineScope.launch {
                            try {
                                viewModel.saveGroup(scope, group)
                                formOpen = false
                                editingId = null
                                error = null
                            } catch (_: Exception) {
                                error = saveError
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.parties_saving) else stringResource(R.string.parties_save_group)) }
            Button(enabled = !saving, onClick = { formOpen = false; editingId = null; formError = false; error = null }) {
                Text(stringResource(R.string.parties_cancel))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.parties_back)) }
            Text(stringResource(R.string.parties_groups_title), style = MaterialTheme.typography.headlineMedium)
            Button(
                enabled = !saving,
                onClick = { form.reset(); editingId = null; formError = false; error = null; formOpen = true },
            ) { Text(stringResource(R.string.parties_add_group)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (groups.isEmpty()) {
                Text(stringResource(R.string.parties_groups_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.id }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.parties_group_members, parties.count { it.groupId == group.id }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        group.priceLevel?.let {
                            Text(stringResource(R.string.parties_group_price_line, it), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = { form.load(group); editingId = group.id; formError = false; error = null; formOpen = true },
                            ) { Text(stringResource(R.string.parties_edit, group.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingId = group.id },
                            ) { Text(stringResource(R.string.parties_delete, group.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.parties_delete_group_confirm)) },
            text = { Text(stringResource(R.string.parties_delete_group_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteGroup(scope, id)
                            deletingId = null
                            error = null
                        } catch (_: Exception) {
                            error = deleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.parties_delete_group_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingId = null }) {
                    Text(stringResource(R.string.parties_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
