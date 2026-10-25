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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Offline-first party master data (spec §15–§16), always scoped to the
 * active business. Two modes: the party list (name, type, opening-balance
 * direction) and the full §16 form. All reads/writes go through the
 * ViewModel's typed surface; the store stays the Phase 12 "customers"
 * table with its tombstone delete semantics.
 */
@Composable
fun PartiesScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: PartiesViewModel = hiltViewModel(),
    ledgerTabsProvider: (suspend (String) -> PartyLedgerTabsData?)? = null,
) {
    val parties by remember(viewModel, scope) { viewModel.observeParties(scope) }
        .collectAsState(initial = emptyList())
    val groups by remember(viewModel, scope) { viewModel.observeGroups(scope) }
        .collectAsState(initial = emptyList())
    val form = remember(scope) { PartyFormState() }
    var formOpen by remember(scope) { mutableStateOf(false) }
    var groupsOpen by remember(scope) { mutableStateOf(false) }
    var profileId by remember(scope) { mutableStateOf<String?>(null) }
    val profileParty = profileId?.let { id -> parties.firstOrNull { it.id == id } }
    var ledgerTabs by remember { mutableStateOf<PartyLedgerTabsData?>(null) }
    LaunchedEffect(profileParty?.id) {
        ledgerTabs = profileParty?.let { ledgerTabsProvider?.invoke(it.id) }
    }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.parties_error)
    val deleteError = stringResource(R.string.parties_delete_error)

    if (formOpen) {
        PartyForm(
            form = form,
            groups = groups,
            isEditing = editingId != null,
            saving = saving,
            formError = formError,
            saveError = error,
            onSave = {
                val party = form.toParty(editingId ?: UUID.randomUUID().toString())
                if (party == null) {
                    formError = true
                } else {
                    formError = false
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveParty(scope, party)
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
            onCancel = { formOpen = false; editingId = null; formError = false; error = null },
        )
    } else if (groupsOpen) {
        PartyGroupsMode(
            scope = scope,
            parties = parties,
            groups = groups,
            viewModel = viewModel,
            onBack = { groupsOpen = false },
        )
    } else if (profileParty != null) {
        PartyProfileMode(
            party = profileParty,
            groupName = profileParty.groupId?.let { gid -> groups.firstOrNull { it.id == gid }?.name },
            onBack = { profileId = null },
            ledgerTabs = ledgerTabs,
            onEdit = {
                form.load(profileParty)
                editingId = profileParty.id
                formError = false
                error = null
                profileId = null
                formOpen = true
            },
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.parties_back)) }
            Text(stringResource(R.string.parties_title), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !saving,
                    onClick = { form.reset(); editingId = null; formError = false; error = null; formOpen = true },
                ) { Text(stringResource(R.string.parties_add)) }
                Button(
                    enabled = !saving,
                    onClick = { groupsOpen = true },
                ) { Text(stringResource(R.string.parties_groups)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (parties.isEmpty()) {
                Text(stringResource(R.string.parties_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(parties, key = { it.id }) { party ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(party.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(party.type.labelRes()), style = MaterialTheme.typography.bodyMedium)
                        party.groupId?.let { groupId ->
                            Text(
                                stringResource(
                                    R.string.parties_group_line,
                                    groups.firstOrNull { it.id == groupId }?.name ?: "—",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        party.openingBalanceDirection()?.let { direction ->
                            val amount = party.openingBalance!!.toPlainString()
                            Text(
                                stringResource(
                                    when (direction) {
                                        OpeningBalanceDirection.RECEIVABLE -> R.string.parties_receivable
                                        OpeningBalanceDirection.PAYABLE -> R.string.parties_payable
                                        OpeningBalanceDirection.NEUTRAL -> R.string.parties_opening
                                    },
                                    amount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (party.isBlocked) {
                            Text(stringResource(R.string.parties_blocked), color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = { profileId = party.id },
                            ) { Text(stringResource(R.string.parties_view, party.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { form.load(party); editingId = party.id; formError = false; error = null; formOpen = true },
                            ) { Text(stringResource(R.string.parties_edit, party.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingId = party.id },
                            ) { Text(stringResource(R.string.parties_delete, party.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.parties_delete_confirm)) },
            text = { Text(stringResource(R.string.parties_delete_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.delete(scope, id)
                            deletingId = null
                            error = null
                        } catch (_: Exception) {
                            error = deleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.parties_delete_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingId = null }) {
                    Text(stringResource(R.string.parties_cancel))
                }
            },
        )
    }
}

/** The full §16 field set as one scrollable form. */
@Composable
private fun PartyForm(
    form: PartyFormState,
    groups: List<PartyGroup>,
    isEditing: Boolean,
    saving: Boolean,
    formError: Boolean,
    saveError: String?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (isEditing) R.string.parties_edit_title else R.string.parties_new_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        PartyTextField(form.name, { form.name = it }, R.string.parties_label)
        Text(stringResource(R.string.parties_type), style = MaterialTheme.typography.titleMedium)
        PartyType.entries.forEach { type ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = form.type == type,
                    onClick = { form.type = type },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = form.type == type, onClick = { form.type = type })
                Text(stringResource(type.labelRes()))
            }
        }
        Text(stringResource(R.string.parties_group), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().selectable(
                selected = form.groupId == null,
                onClick = { form.groupId = null },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = form.groupId == null, onClick = { form.groupId = null })
            Text(stringResource(R.string.parties_group_none))
        }
        groups.forEach { group ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = form.groupId == group.id,
                    onClick = { form.groupId = group.id },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = form.groupId == group.id, onClick = { form.groupId = group.id })
                Text(group.name)
            }
        }
        PartyTextField(form.businessName, { form.businessName = it }, R.string.parties_business_name)
        PartyTextField(form.contactPerson, { form.contactPerson = it }, R.string.parties_contact_person)
        PartyTextField(form.mobile, { form.mobile = it }, R.string.parties_mobile, KeyboardType.Phone)
        PartyTextField(form.whatsapp, { form.whatsapp = it }, R.string.parties_whatsapp, KeyboardType.Phone)
        PartyTextField(form.phone, { form.phone = it }, R.string.parties_phone, KeyboardType.Phone)
        PartyTextField(form.email, { form.email = it }, R.string.parties_email, KeyboardType.Email)
        Text(stringResource(R.string.parties_addresses), style = MaterialTheme.typography.titleMedium)
        form.addresses.forEachIndexed { index, address ->
            AddressFields(address) {
                form.addresses = form.addresses.filterIndexed { i, _ -> i != index }
            }
        }
        Button(onClick = { form.addresses = form.addresses + PartyAddressFormState() }) {
            Text(stringResource(R.string.parties_add_address))
        }
        PartyTextField(form.taxNumber, { form.taxNumber = it }, R.string.parties_tax_number)
        PartyTextField(form.nationalId, { form.nationalId = it }, R.string.parties_national_id)
        PartyTextField(form.openingBalance, { form.openingBalance = it }, R.string.parties_opening_balance, KeyboardType.Decimal)
        PartyTextField(form.creditLimit, { form.creditLimit = it }, R.string.parties_credit_limit, KeyboardType.Decimal)
        PartyTextField(form.creditDays, { form.creditDays = it }, R.string.parties_credit_days, KeyboardType.Number)
        PartyTextField(form.paymentTerms, { form.paymentTerms = it }, R.string.parties_payment_terms)
        PartyTextField(form.priceLevel, { form.priceLevel = it }, R.string.parties_price_level)
        PartyTextField(form.territory, { form.territory = it }, R.string.parties_territory)
        PartyTextField(form.salesperson, { form.salesperson = it }, R.string.parties_salesperson)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.isBlocked, onCheckedChange = { form.isBlocked = it })
            Text(stringResource(R.string.parties_blocked))
        }
        PartyTextField(form.notes, { form.notes = it }, R.string.parties_notes)
        if (formError) {
            Text(stringResource(R.string.parties_form_error), color = MaterialTheme.colorScheme.error)
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = form.name.isNotBlank() && !saving, onClick = onSave) {
            Text(if (saving) stringResource(R.string.parties_saving) else stringResource(R.string.parties_save))
        }
        Button(enabled = !saving, onClick = onCancel) {
            Text(stringResource(R.string.parties_cancel))
        }
    }
}

/** One address block in the form: kind toggle, the six fields, remove. */
@Composable
private fun AddressFields(address: PartyAddressFormState, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { address.kind = PartyAddress.Kind.BILLING }, enabled = address.kind != PartyAddress.Kind.BILLING) {
                Text(stringResource(R.string.parties_address_billing))
            }
            Button(onClick = { address.kind = PartyAddress.Kind.SHIPPING }, enabled = address.kind != PartyAddress.Kind.SHIPPING) {
                Text(stringResource(R.string.parties_address_shipping))
            }
        }
        PartyTextField(address.line1, { address.line1 = it }, R.string.parties_address_line1)
        PartyTextField(address.line2, { address.line2 = it }, R.string.parties_address_line2)
        PartyTextField(address.city, { address.city = it }, R.string.parties_address_city)
        PartyTextField(address.region, { address.region = it }, R.string.parties_address_region)
        PartyTextField(address.postalCode, { address.postalCode = it }, R.string.parties_address_postal)
        PartyTextField(address.country, { address.country = it }, R.string.parties_address_country)
        Button(onClick = onRemove) { Text(stringResource(R.string.parties_remove_address)) }
    }
}

@Composable
private fun PartyTextField(
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

fun PartyType.labelRes(): Int = when (this) {
    PartyType.CUSTOMER -> R.string.parties_type_customer
    PartyType.SUPPLIER -> R.string.parties_type_supplier
    PartyType.BOTH -> R.string.parties_type_both
    PartyType.OTHER -> R.string.parties_type_other
}
