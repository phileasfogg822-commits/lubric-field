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
import com.lubrcapp.lubrcapp.core.designsystem.DateField
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Phase 16.4 — batch management (§29): every batch across items
 * with its item name, expiry and quantity lines, and the batch
 * form with the item picker. Quantities are stored data only —
 * nothing here moves stock (the ledger is Phase 19). Batches use
 * the same tombstone delete flow as items; deleting an item never
 * edits its batches — their item link stays and renders "—".
 */
@Composable
fun ItemBatchesMode(
    scope: OperationalScope,
    items: List<Item>,
    batches: List<Batch>,
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val form = remember(scope) { BatchFormState() }
    var formOpen by remember(scope) { mutableStateOf(false) }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.items_batch_error)
    val deleteError = stringResource(R.string.items_batch_delete_error)

    if (formOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingId != null) R.string.items_edit_batch else R.string.items_new_batch),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.items_batch_item), style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = form.itemId == item.id,
                        onClick = { form.itemId = item.id },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = form.itemId == item.id, onClick = { form.itemId = item.id })
                    Text(item.name)
                }
            }
            BatchTextField(form.batchNumber, { form.batchNumber = it }, R.string.items_batch_number)
            DateField(form.manufacturingDate, { form.manufacturingDate = it }, stringResource(R.string.items_batch_mfg_date), Modifier.fillMaxWidth())
            DateField(form.expiryDate, { form.expiryDate = it }, stringResource(R.string.items_batch_expiry_date), Modifier.fillMaxWidth())
            BatchTextField(form.purchaseRate, { form.purchaseRate = it }, R.string.items_purchase_rate, KeyboardType.Decimal)
            BatchTextField(form.saleRate, { form.saleRate = it }, R.string.items_sale_rate, KeyboardType.Decimal)
            BatchTextField(form.mrp, { form.mrp = it }, R.string.items_mrp, KeyboardType.Decimal)
            BatchTextField(form.quantity, { form.quantity = it }, R.string.items_batch_quantity, KeyboardType.Decimal)
            if (formError) {
                Text(stringResource(R.string.items_batch_form_error), color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = form.itemId != null && form.batchNumber.isNotBlank() && !saving,
                onClick = {
                    val batch = form.toBatch(editingId ?: UUID.randomUUID().toString())
                    if (batch == null) {
                        formError = true
                    } else {
                        formError = false
                        saving = true
                        coroutineScope.launch {
                            try {
                                viewModel.saveBatch(scope, batch)
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
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_batch)) }
            Button(enabled = !saving, onClick = { formOpen = false; editingId = null; formError = false; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_batches_title), style = MaterialTheme.typography.headlineMedium)
            Button(
                enabled = !saving,
                onClick = { form.reset(); editingId = null; formError = false; error = null; formOpen = true },
            ) { Text(stringResource(R.string.items_add_batch)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (batches.isEmpty()) {
                Text(stringResource(R.string.items_batches_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(batches, key = { it.id }) { batch ->
                    val itemName = items.firstOrNull { it.id == batch.itemId }?.name ?: "—"
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.items_batch_line, itemName, batch.batchNumber),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        batch.expiryDate?.let { expiry ->
                            Text(
                                stringResource(R.string.items_batch_expiry_line, expiry),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        batch.quantity?.let { qty ->
                            Text(
                                stringResource(R.string.items_batch_quantity_line, qty.toPlainString()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    form.load(batch)
                                    editingId = batch.id
                                    formError = false
                                    error = null
                                    formOpen = true
                                },
                            ) { Text(stringResource(R.string.items_edit, batch.batchNumber)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingId = batch.id },
                            ) { Text(stringResource(R.string.items_delete, batch.batchNumber)) }
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.items_delete_batch_confirm)) },
            text = { Text(stringResource(R.string.items_delete_batch_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteBatch(scope, id)
                            deletingId = null
                            error = null
                        } catch (_: Exception) {
                            error = deleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_batch_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
}

@Composable
private fun BatchTextField(
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
