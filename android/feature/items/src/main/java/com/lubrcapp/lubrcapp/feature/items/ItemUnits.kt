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
 * Phase 16.1 — unit and conversion management (§25): the unit list
 * with item counts, the unit form with the §25 suggested names as
 * quick picks, the conversion list, and the conversion form with
 * from/to unit pickers. Both entities use the same tombstone
 * delete flow as items. Deleting a unit never edits the items or
 * conversions that link to it — their ids stay and render
 * unresolved ("—").
 */
@Composable
fun ItemUnitsMode(
    scope: OperationalScope,
    items: List<Item>,
    units: List<ItemUnit>,
    conversions: List<UnitConversion>,
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val unitForm = remember(scope) { ItemUnitFormState() }
    val conversionForm = remember(scope) { UnitConversionFormState() }
    var unitFormOpen by remember(scope) { mutableStateOf(false) }
    var conversionFormOpen by remember(scope) { mutableStateOf(false) }
    var editingUnitId by remember(scope) { mutableStateOf<String?>(null) }
    var editingConversionId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingUnitId by remember(scope) { mutableStateOf<String?>(null) }
    var deletingConversionId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var conversionFormError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val unitSaveError = stringResource(R.string.items_unit_error)
    val unitDeleteError = stringResource(R.string.items_unit_delete_error)
    val conversionSaveError = stringResource(R.string.items_conversion_error)
    val conversionDeleteError = stringResource(R.string.items_conversion_delete_error)

    if (unitFormOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingUnitId != null) R.string.items_edit_unit else R.string.items_new_unit),
                style = MaterialTheme.typography.headlineMedium,
            )
            UnitTextField(unitForm.name, { unitForm.name = it }, R.string.items_unit_name)
            Text(stringResource(R.string.items_common_units), style = MaterialTheme.typography.titleMedium)
            SUGGESTED_UNIT_NAMES.forEach { suggested ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = unitForm.name == suggested,
                        onClick = { unitForm.name = suggested },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = unitForm.name == suggested, onClick = { unitForm.name = suggested })
                    Text(suggested)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = unitForm.name.isNotBlank() && !saving,
                onClick = {
                    val unit = unitForm.toUnit(editingUnitId ?: UUID.randomUUID().toString())
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveUnit(scope, unit)
                            unitFormOpen = false
                            editingUnitId = null
                            error = null
                        } catch (_: Exception) {
                            error = unitSaveError
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_unit)) }
            Button(enabled = !saving, onClick = { unitFormOpen = false; editingUnitId = null; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else if (conversionFormOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (editingConversionId != null) R.string.items_edit_conversion else R.string.items_new_conversion,
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.items_conversion_from), style = MaterialTheme.typography.titleMedium)
            units.forEach { unit ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = conversionForm.fromUnitId == unit.id,
                        onClick = { conversionForm.fromUnitId = unit.id },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = conversionForm.fromUnitId == unit.id,
                        onClick = { conversionForm.fromUnitId = unit.id },
                    )
                    Text(unit.name)
                }
            }
            Text(stringResource(R.string.items_conversion_to), style = MaterialTheme.typography.titleMedium)
            units.forEach { unit ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = conversionForm.toUnitId == unit.id,
                        onClick = { conversionForm.toUnitId = unit.id },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = conversionForm.toUnitId == unit.id,
                        onClick = { conversionForm.toUnitId = unit.id },
                    )
                    Text(unit.name)
                }
            }
            UnitTextField(
                conversionForm.factor,
                { conversionForm.factor = it },
                R.string.items_conversion_factor,
                KeyboardType.Decimal,
            )
            if (conversionFormError) {
                Text(stringResource(R.string.items_conversion_form_error), color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = conversionForm.toConversion(editingConversionId ?: "preview") != null && !saving,
                onClick = {
                    val conversion = conversionForm.toConversion(
                        editingConversionId ?: UUID.randomUUID().toString(),
                    )
                    if (conversion == null) {
                        conversionFormError = true
                    } else {
                        conversionFormError = false
                        saving = true
                        coroutineScope.launch {
                            try {
                                viewModel.saveConversion(scope, conversion)
                                conversionFormOpen = false
                                editingConversionId = null
                                error = null
                            } catch (_: Exception) {
                                error = conversionSaveError
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            ) {
                Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_conversion))
            }
            Button(
                enabled = !saving,
                onClick = { conversionFormOpen = false; editingConversionId = null; conversionFormError = false; error = null },
            ) { Text(stringResource(R.string.items_cancel)) }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_units_title), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !saving,
                    onClick = { unitForm.reset(); editingUnitId = null; error = null; unitFormOpen = true },
                ) { Text(stringResource(R.string.items_add_unit)) }
                Button(
                    enabled = !saving && units.size >= 2,
                    onClick = {
                        conversionForm.reset()
                        editingConversionId = null
                        conversionFormError = false
                        error = null
                        conversionFormOpen = true
                    },
                ) { Text(stringResource(R.string.items_add_conversion)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (units.isEmpty()) {
                    item { Text(stringResource(R.string.items_units_empty)) }
                }
                items(units, key = { it.id }) { unit ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(unit.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.items_unit_items, items.count { it.unitId == unit.id }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    unitForm.load(unit)
                                    editingUnitId = unit.id
                                    error = null
                                    unitFormOpen = true
                                },
                            ) { Text(stringResource(R.string.items_edit, unit.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingUnitId = unit.id },
                            ) { Text(stringResource(R.string.items_delete, unit.name)) }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.items_conversions_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                if (conversions.isEmpty()) {
                    item { Text(stringResource(R.string.items_conversions_empty)) }
                }
                items(conversions, key = { it.id }) { conversion ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(
                                R.string.items_conversion_line,
                                units.firstOrNull { it.id == conversion.fromUnitId }?.name ?: "—",
                                units.firstOrNull { it.id == conversion.toUnitId }?.name ?: "—",
                                conversion.factor.toPlainString(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    conversionForm.load(conversion)
                                    editingConversionId = conversion.id
                                    conversionFormError = false
                                    error = null
                                    conversionFormOpen = true
                                },
                            ) {
                                Text(
                                    stringResource(
                                        R.string.items_edit_conversion_entry,
                                        units.firstOrNull { it.id == conversion.fromUnitId }?.name ?: "—",
                                        units.firstOrNull { it.id == conversion.toUnitId }?.name ?: "—",
                                    ),
                                )
                            }
                            Button(
                                enabled = !saving,
                                onClick = { deletingConversionId = conversion.id },
                            ) {
                                Text(
                                    stringResource(
                                        R.string.items_delete_conversion_entry,
                                        units.firstOrNull { it.id == conversion.fromUnitId }?.name ?: "—",
                                        units.firstOrNull { it.id == conversion.toUnitId }?.name ?: "—",
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    deletingUnitId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingUnitId = null },
            title = { Text(stringResource(R.string.items_delete_unit_confirm)) },
            text = { Text(stringResource(R.string.items_delete_unit_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteUnit(scope, id)
                            deletingUnitId = null
                            error = null
                        } catch (_: Exception) {
                            error = unitDeleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_unit_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingUnitId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
    deletingConversionId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingConversionId = null },
            title = { Text(stringResource(R.string.items_delete_conversion_confirm)) },
            text = { Text(stringResource(R.string.items_delete_conversion_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteConversion(scope, id)
                            deletingConversionId = null
                            error = null
                        } catch (_: Exception) {
                            error = conversionDeleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_conversion_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingConversionId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
}

@Composable
private fun UnitTextField(
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
