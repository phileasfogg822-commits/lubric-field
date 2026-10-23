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
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Phase 16.2 — branch and warehouse management (§34): the branch
 * list with warehouse counts, the warehouse list with branch
 * lines, and both forms (the warehouse form carries the branch
 * picker). Both entities use the same tombstone delete flow as
 * items. Deleting a branch never edits its warehouses — their
 * links stay and render unresolved ("—").
 */
@Composable
fun ItemLocationsMode(
    scope: OperationalScope,
    branches: List<Branch>,
    warehouses: List<Warehouse>,
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val branchForm = remember(scope) { BranchFormState() }
    val warehouseForm = remember(scope) { WarehouseFormState() }
    var branchFormOpen by remember(scope) { mutableStateOf(false) }
    var warehouseFormOpen by remember(scope) { mutableStateOf(false) }
    var editingBranchId by remember(scope) { mutableStateOf<String?>(null) }
    var editingWarehouseId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingBranchId by remember(scope) { mutableStateOf<String?>(null) }
    var deletingWarehouseId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val branchSaveError = stringResource(R.string.items_branch_error)
    val branchDeleteError = stringResource(R.string.items_branch_delete_error)
    val warehouseSaveError = stringResource(R.string.items_warehouse_error)
    val warehouseDeleteError = stringResource(R.string.items_warehouse_delete_error)

    if (branchFormOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingBranchId != null) R.string.items_edit_branch else R.string.items_new_branch),
                style = MaterialTheme.typography.headlineMedium,
            )
            LocationTextField(branchForm.name, { branchForm.name = it }, R.string.items_branch_name)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = branchForm.name.isNotBlank() && !saving,
                onClick = {
                    val branch = branchForm.toBranch(editingBranchId ?: UUID.randomUUID().toString())
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveBranch(scope, branch)
                            branchFormOpen = false
                            editingBranchId = null
                            error = null
                        } catch (_: Exception) {
                            error = branchSaveError
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_branch)) }
            Button(enabled = !saving, onClick = { branchFormOpen = false; editingBranchId = null; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else if (warehouseFormOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (editingWarehouseId != null) R.string.items_edit_warehouse else R.string.items_new_warehouse,
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            LocationTextField(warehouseForm.name, { warehouseForm.name = it }, R.string.items_warehouse_name)
            Text(stringResource(R.string.items_warehouse_branch), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = warehouseForm.branchId == null,
                    onClick = { warehouseForm.branchId = null },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = warehouseForm.branchId == null, onClick = { warehouseForm.branchId = null })
                Text(stringResource(R.string.items_warehouse_branch_none))
            }
            branches.forEach { branch ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = warehouseForm.branchId == branch.id,
                        onClick = { warehouseForm.branchId = branch.id },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = warehouseForm.branchId == branch.id,
                        onClick = { warehouseForm.branchId = branch.id },
                    )
                    Text(branch.name)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = warehouseForm.name.isNotBlank() && !saving,
                onClick = {
                    val warehouse = warehouseForm.toWarehouse(editingWarehouseId ?: UUID.randomUUID().toString())
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveWarehouse(scope, warehouse)
                            warehouseFormOpen = false
                            editingWarehouseId = null
                            error = null
                        } catch (_: Exception) {
                            error = warehouseSaveError
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_warehouse))
            }
            Button(enabled = !saving, onClick = { warehouseFormOpen = false; editingWarehouseId = null; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_locations_title), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !saving,
                    onClick = { branchForm.reset(); editingBranchId = null; error = null; branchFormOpen = true },
                ) { Text(stringResource(R.string.items_add_branch)) }
                Button(
                    enabled = !saving,
                    onClick = { warehouseForm.reset(); editingWarehouseId = null; error = null; warehouseFormOpen = true },
                ) { Text(stringResource(R.string.items_add_warehouse)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (branches.isEmpty()) {
                    item { Text(stringResource(R.string.items_branches_empty)) }
                }
                items(branches, key = { it.id }) { branch ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(branch.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.items_branch_warehouses,
                                warehouses.count { it.branchId == branch.id },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    branchForm.load(branch)
                                    editingBranchId = branch.id
                                    error = null
                                    branchFormOpen = true
                                },
                            ) { Text(stringResource(R.string.items_edit, branch.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingBranchId = branch.id },
                            ) { Text(stringResource(R.string.items_delete, branch.name)) }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.items_warehouses_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                if (warehouses.isEmpty()) {
                    item { Text(stringResource(R.string.items_warehouses_empty)) }
                }
                items(warehouses, key = { it.id }) { warehouse ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(warehouse.name, style = MaterialTheme.typography.titleMedium)
                        warehouse.branchId?.let { branchId ->
                            Text(
                                stringResource(
                                    R.string.items_warehouse_branch_line,
                                    branches.firstOrNull { it.id == branchId }?.name ?: "—",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    warehouseForm.load(warehouse)
                                    editingWarehouseId = warehouse.id
                                    error = null
                                    warehouseFormOpen = true
                                },
                            ) { Text(stringResource(R.string.items_edit, warehouse.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingWarehouseId = warehouse.id },
                            ) { Text(stringResource(R.string.items_delete, warehouse.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingBranchId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingBranchId = null },
            title = { Text(stringResource(R.string.items_delete_branch_confirm)) },
            text = { Text(stringResource(R.string.items_delete_branch_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteBranch(scope, id)
                            deletingBranchId = null
                            error = null
                        } catch (_: Exception) {
                            error = branchDeleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_branch_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingBranchId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
    deletingWarehouseId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingWarehouseId = null },
            title = { Text(stringResource(R.string.items_delete_warehouse_confirm)) },
            text = { Text(stringResource(R.string.items_delete_warehouse_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteWarehouse(scope, id)
                            deletingWarehouseId = null
                            error = null
                        } catch (_: Exception) {
                            error = warehouseDeleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_warehouse_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingWarehouseId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
}

@Composable
private fun LocationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
    )
}
