package com.lubrcapp.lubrcapp.feature.items

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Phase 16.3 — price-list management (§26): the list of price
 * lists with entry counts, one list's entries (item name + rate),
 * and both forms. Everything uses the same tombstone delete flow
 * as items. Deleting a list never edits its entries — they keep
 * their list link, show in no list, and stop resolving.
 */
@Composable
fun ItemPriceListsMode(
    scope: OperationalScope,
    items: List<Item>,
    priceLists: List<PriceList>,
    priceListItems: List<PriceListItem>,
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val listForm = remember(scope) { PriceListFormState() }
    val entryForm = remember(scope) { PriceListItemFormState() }
    var listFormOpen by remember(scope) { mutableStateOf(false) }
    var entryFormOpen by remember(scope) { mutableStateOf(false) }
    var editingListId by remember(scope) { mutableStateOf<String?>(null) }
    var editingEntryId by remember(scope) { mutableStateOf<String?>(null) }
    var selectedListId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingListId by remember(scope) { mutableStateOf<String?>(null) }
    var deletingEntryId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var entryFormError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listSaveError = stringResource(R.string.items_price_list_error)
    val listDeleteError = stringResource(R.string.items_price_list_delete_error)
    val entrySaveError = stringResource(R.string.items_price_entry_error)
    val entryDeleteError = stringResource(R.string.items_price_entry_delete_error)
    val selected = priceLists.firstOrNull { it.id == selectedListId }

    if (listFormOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingListId != null) R.string.items_edit_price_list else R.string.items_new_price_list),
                style = MaterialTheme.typography.headlineMedium,
            )
            PriceListTextField(listForm.name, { listForm.name = it }, R.string.items_price_list_name)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = listForm.name.isNotBlank() && !saving,
                onClick = {
                    val priceList = listForm.toPriceList(editingListId ?: UUID.randomUUID().toString())
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.savePriceList(scope, priceList)
                            listFormOpen = false
                            editingListId = null
                            error = null
                        } catch (_: Exception) {
                            error = listSaveError
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_price_list)) }
            Button(enabled = !saving, onClick = { listFormOpen = false; editingListId = null; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else if (entryFormOpen && selected != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingEntryId != null) R.string.items_edit_price_entry else R.string.items_new_price_entry),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.items_price_entry_item), style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = entryForm.itemId == item.id,
                        onClick = { entryForm.itemId = item.id },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = entryForm.itemId == item.id, onClick = { entryForm.itemId = item.id })
                    Text(item.name)
                }
            }
            PriceListTextField(entryForm.rate, { entryForm.rate = it }, R.string.items_price_entry_rate, KeyboardType.Decimal)
            if (entryFormError) {
                Text(stringResource(R.string.items_price_entry_form_error), color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = entryForm.toEntry(editingEntryId ?: "preview", selected.id) != null && !saving,
                onClick = {
                    val entry = entryForm.toEntry(editingEntryId ?: UUID.randomUUID().toString(), selected.id)
                    if (entry == null) {
                        entryFormError = true
                    } else {
                        entryFormError = false
                        saving = true
                        coroutineScope.launch {
                            try {
                                viewModel.savePriceListItem(scope, entry)
                                entryFormOpen = false
                                editingEntryId = null
                                error = null
                            } catch (_: Exception) {
                                error = entrySaveError
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_price_entry)) }
            Button(
                enabled = !saving,
                onClick = { entryFormOpen = false; editingEntryId = null; entryFormError = false; error = null },
            ) { Text(stringResource(R.string.items_cancel)) }
        }
    } else if (selected != null) {
        val entries = priceListItems.filter { it.priceListId == selected.id }
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { selectedListId = null }) { Text(stringResource(R.string.items_back)) }
            Text(selected.name, style = MaterialTheme.typography.headlineMedium)
            Button(
                enabled = !saving,
                onClick = { entryForm.reset(); editingEntryId = null; entryFormError = false; error = null; entryFormOpen = true },
            ) { Text(stringResource(R.string.items_add_price_entry)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (entries.isEmpty()) {
                Text(stringResource(R.string.items_price_entries_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    val itemName = items.firstOrNull { it.id == entry.itemId }?.name ?: "—"
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(
                                R.string.items_price_entry_line,
                                itemName,
                                entry.rate?.toPlainString() ?: "—",
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    entryForm.load(entry)
                                    editingEntryId = entry.id
                                    entryFormError = false
                                    error = null
                                    entryFormOpen = true
                                },
                            ) { Text(stringResource(R.string.items_edit, itemName)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingEntryId = entry.id },
                            ) { Text(stringResource(R.string.items_delete, itemName)) }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_price_lists_title), style = MaterialTheme.typography.headlineMedium)
            Button(
                enabled = !saving,
                onClick = { listForm.reset(); editingListId = null; error = null; listFormOpen = true },
            ) { Text(stringResource(R.string.items_add_price_list)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (priceLists.isEmpty()) {
                Text(stringResource(R.string.items_price_lists_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(priceLists, key = { it.id }) { priceList ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(priceList.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.items_price_list_entries,
                                priceListItems.count { it.priceListId == priceList.id },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = { selectedListId = priceList.id },
                            ) { Text(stringResource(R.string.items_view_price_list, priceList.name)) }
                            Button(
                                enabled = !saving,
                                onClick = {
                                    listForm.load(priceList)
                                    editingListId = priceList.id
                                    error = null
                                    listFormOpen = true
                                },
                            ) { Text(stringResource(R.string.items_edit, priceList.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingListId = priceList.id },
                            ) { Text(stringResource(R.string.items_delete, priceList.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingListId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingListId = null },
            title = { Text(stringResource(R.string.items_delete_price_list_confirm)) },
            text = { Text(stringResource(R.string.items_delete_price_list_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deletePriceList(scope, id)
                            deletingListId = null
                            error = null
                        } catch (_: Exception) {
                            error = listDeleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_price_list_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingListId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
    deletingEntryId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingEntryId = null },
            title = { Text(stringResource(R.string.items_delete_price_entry_confirm)) },
            text = { Text(stringResource(R.string.items_delete_price_entry_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deletePriceListItem(scope, id)
                            deletingEntryId = null
                            error = null
                        } catch (_: Exception) {
                            error = entryDeleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_price_entry_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingEntryId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
}

@Composable
private fun PriceListTextField(
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
