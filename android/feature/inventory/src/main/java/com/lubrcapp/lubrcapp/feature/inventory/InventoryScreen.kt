package com.lubrcapp.lubrcapp.feature.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.lubrcapp.lubrcapp.domain.inventory.StockStatus
import java.math.BigDecimal
import kotlinx.coroutines.launch

/**
 * Phase 19.3 — the §31 inventory screen over the 19.1 ledger.
 * Cards (value / items / low stock) and every row's balance and
 * §32 status are computed by the 19.2 engine from the movements;
 * nothing stock-shaped is stored. An item's detail shows its
 * movement history and its §33 levels editor.
 */
@Composable
fun InventoryScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val items by remember(viewModel, scope) { viewModel.observeItems(scope) }
        .collectAsState(initial = emptyList())
    val movements by remember(viewModel, scope) { viewModel.observeMovements(scope) }
        .collectAsState(initial = emptyList())
    val levels by remember(viewModel, scope) { viewModel.observeLevels(scope) }
        .collectAsState(initial = emptyMap())
    val warehouses by remember(viewModel, scope) { viewModel.observeWarehouses(scope) }
        .collectAsState(initial = emptyList())
    val categories by remember(viewModel, scope) { viewModel.observeCategories(scope) }
        .collectAsState(initial = emptyList())
    val brands by remember(viewModel, scope) { viewModel.observeBrands(scope) }
        .collectAsState(initial = emptyList())

    var selectedItemId by remember(scope) { mutableStateOf<String?>(null) }
    var statusFilter by remember(scope) { mutableStateOf<StockStatus?>(null) }
    var warehouseFilter by remember(scope) { mutableStateOf<String?>(null) }
    var categoryFilter by remember(scope) { mutableStateOf<String?>(null) }
    var brandFilter by remember(scope) { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var saving by remember(scope) { mutableStateOf(false) }
    var error by remember(scope) { mutableStateOf<String?>(null) }
    val saveError = stringResource(R.string.inventory_error)

    val summary = remember(items, movements, levels, warehouseFilter) {
        InventorySummaryBuilder.build(items, movements, levels, warehouseFilter)
    }
    val selectedRow = summary.rows.firstOrNull { it.item.id == selectedItemId }
    val visibleRows = summary.rows.filter { row ->
        (statusFilter == null || row.status == statusFilter) &&
            (categoryFilter == null || row.item.categoryId == categoryFilter) &&
            (brandFilter == null || row.item.brandId == brandFilter)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectedRow == null) {
            Button(onClick = onBack) { Text(stringResource(R.string.inventory_back)) }
            Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.inventory_value_card, summary.totalValue.toPlainString()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.inventory_items_card, summary.itemCount))
            Text(stringResource(R.string.inventory_low_card, summary.lowStockCount))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Text(stringResource(R.string.inventory_filter_status), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { statusFilter = null }, enabled = statusFilter != null) {
                    Text(stringResource(R.string.inventory_filter_all))
                }
                StockStatus.entries.forEach { status ->
                    Button(onClick = { statusFilter = status }, enabled = statusFilter != status) {
                        Text(stringResource(statusLabel(status)))
                    }
                }
            }
            Text(stringResource(R.string.inventory_filter_warehouse), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { warehouseFilter = null }, enabled = warehouseFilter != null) {
                    Text(stringResource(R.string.inventory_filter_all))
                }
                warehouses.forEach { warehouse ->
                    Button(onClick = { warehouseFilter = warehouse.id }, enabled = warehouseFilter != warehouse.id) {
                        Text(warehouse.name)
                    }
                }
            }
            if (categories.isNotEmpty()) {
                Text(stringResource(R.string.inventory_filter_category), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { categoryFilter = null }, enabled = categoryFilter != null) {
                        Text(stringResource(R.string.inventory_filter_all))
                    }
                    categories.forEach { category ->
                        Button(onClick = { categoryFilter = category.id }, enabled = categoryFilter != category.id) {
                            Text(category.name)
                        }
                    }
                }
            }
            if (brands.isNotEmpty()) {
                Text(stringResource(R.string.inventory_filter_brand), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { brandFilter = null }, enabled = brandFilter != null) {
                        Text(stringResource(R.string.inventory_filter_all))
                    }
                    brands.forEach { brand ->
                        Button(onClick = { brandFilter = brand.id }, enabled = brandFilter != brand.id) {
                            Text(brand.name)
                        }
                    }
                }
            }

            if (visibleRows.isEmpty()) {
                Text(stringResource(R.string.inventory_empty_items))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleRows, key = { it.item.id }) { row ->
                    Button(
                        onClick = { selectedItemId = row.item.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.inventory_row,
                                row.item.name,
                                row.balance.toPlainString(),
                                stringResource(statusLabel(row.status)),
                            )
                        )
                    }
                }
            }
        } else {
            val itemMovements = movements.filter { it.itemId == selectedRow.item.id }
            ItemDetail(
                row = selectedRow,
                movements = itemMovements,
                levels = levels[selectedRow.item.id],
                saving = saving,
                error = error,
                onBackToList = { selectedItemId = null; error = null },
                onSaveLevels = { record ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveLevels(scope, record)
                            error = null
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ItemDetail(
    row: InventoryRow,
    movements: List<StockMovementRecord>,
    levels: StockLevelsRecord?,
    saving: Boolean,
    error: String?,
    onBackToList: () -> Unit,
    onSaveLevels: (StockLevelsRecord) -> Unit,
) {
    var minimumText by remember(row.item.id, levels) { mutableStateOf(levels?.minimum?.toPlainString() ?: "") }
    var reorderText by remember(row.item.id, levels) { mutableStateOf(levels?.reorderLevel?.toPlainString() ?: "") }
    var maximumText by remember(row.item.id, levels) { mutableStateOf(levels?.maximum?.toPlainString() ?: "") }

    Button(onClick = onBackToList) { Text(stringResource(R.string.inventory_back_to_list)) }
    Text(row.item.name, style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.inventory_detail_balance, row.balance.toPlainString()))
    Text(stringResource(R.string.inventory_detail_status, stringResource(statusLabel(row.status))))
    if (row.needsReorder) {
        Text(stringResource(R.string.inventory_reorder_due), color = MaterialTheme.colorScheme.error)
    }
    row.value?.let {
        Text(stringResource(R.string.inventory_detail_value, it.toPlainString()))
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Text(stringResource(R.string.inventory_history_title), style = MaterialTheme.typography.titleMedium)
    if (movements.isEmpty()) {
        Text(stringResource(R.string.inventory_history_empty))
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(movements, key = { it.id }) { movement ->
            Text(
                stringResource(
                    R.string.inventory_history_row,
                    movement.date,
                    movement.type.name,
                    movement.quantity.toPlainString(),
                )
            )
        }
    }

    // §33 levels editor — outside the LazyColumn so its Save
    // button is always composed with the current handler
    // (the Phase 18.4 lesson).
    Text(stringResource(R.string.inventory_levels_title), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = minimumText,
        onValueChange = { minimumText = it },
        label = { Text(stringResource(R.string.inventory_level_minimum)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = reorderText,
        onValueChange = { reorderText = it },
        label = { Text(stringResource(R.string.inventory_level_reorder)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = maximumText,
        onValueChange = { maximumText = it },
        label = { Text(stringResource(R.string.inventory_level_maximum)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            onSaveLevels(
                StockLevelsRecord(
                    itemId = row.item.id,
                    minimum = minimumText.toDecimalOrNull(),
                    reorderLevel = reorderText.toDecimalOrNull(),
                    maximum = maximumText.toDecimalOrNull(),
                )
            )
        },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.inventory_levels_save))
    }
}

private fun statusLabel(status: StockStatus): Int = when (status) {
    StockStatus.IN_STOCK -> R.string.inventory_status_in_stock
    StockStatus.LOW_STOCK -> R.string.inventory_status_low_stock
    StockStatus.OUT_OF_STOCK -> R.string.inventory_status_out_of_stock
    StockStatus.OVERSTOCK -> R.string.inventory_status_overstock
    StockStatus.INACTIVE -> R.string.inventory_status_inactive
}

private fun String.toDecimalOrNull(): BigDecimal? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return try {
        BigDecimal(trimmed)
    } catch (e: Exception) {
        null
    }
}
