package com.lubrcapp.lubrcapp.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.inventory.StockStatus
import java.math.BigDecimal
import kotlinx.coroutines.launch

/**
 * Phase 19.3/19.4 — the inventory module screen. List mode is
 * the §31 summary (computed cards, filters, ledger-derived
 * rows); an item's detail shows its movement history and §33
 * levels editor. Adjust mode posts §36 adjustments and Count
 * mode runs the §37 physical count — both write through the
 * ViewModel's §175-checked posting surface, never directly.
 */
private enum class InventoryMode { LIST, ADJUST, COUNT }

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

    var mode by remember(scope) { mutableStateOf(InventoryMode.LIST) }
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
        when (mode) {
            InventoryMode.ADJUST -> StockAdjustmentMode(
                items = items,
                warehouses = warehouses,
                saving = saving,
                onDone = { mode = InventoryMode.LIST },
                onPost = { form, onResult ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            onResult(viewModel.postAdjustment(scope, form))
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
            )
            InventoryMode.COUNT -> StockCountMode(
                rows = summary.rows,
                saving = saving,
                onDone = { mode = InventoryMode.LIST },
                onAdjust = { itemId, variance, reason, onResult ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            onResult(viewModel.postCountVariance(scope, itemId, variance, reason = reason))
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
            )
            InventoryMode.LIST -> {
                if (selectedRow == null) {
                    InventoryList(
                        summary = summary,
                        visibleRows = visibleRows,
                        warehouses = warehouses,
                        categories = categories,
                        brands = brands,
                        statusFilter = statusFilter,
                        warehouseFilter = warehouseFilter,
                        categoryFilter = categoryFilter,
                        brandFilter = brandFilter,
                        error = error,
                        onBack = onBack,
                        onStatusFilter = { statusFilter = it },
                        onWarehouseFilter = { warehouseFilter = it },
                        onCategoryFilter = { categoryFilter = it },
                        onBrandFilter = { brandFilter = it },
                        onSelect = { selectedItemId = it },
                        onAdjust = { mode = InventoryMode.ADJUST },
                        onCount = { mode = InventoryMode.COUNT },
                    )
                } else {
                    ItemDetail(
                        row = selectedRow,
                        movements = movements.filter { it.itemId == selectedRow.item.id },
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
    }
}

@Composable
private fun ColumnScope.InventoryList(
    summary: InventorySummary,
    visibleRows: List<InventoryRow>,
    warehouses: List<NamedOption>,
    categories: List<NamedOption>,
    brands: List<NamedOption>,
    statusFilter: StockStatus?,
    warehouseFilter: String?,
    categoryFilter: String?,
    brandFilter: String?,
    error: String?,
    onBack: () -> Unit,
    onStatusFilter: (StockStatus?) -> Unit,
    onWarehouseFilter: (String?) -> Unit,
    onCategoryFilter: (String?) -> Unit,
    onBrandFilter: (String?) -> Unit,
    onSelect: (String) -> Unit,
    onAdjust: () -> Unit,
    onCount: () -> Unit,
) {
    Button(onClick = onBack) { Text(stringResource(R.string.inventory_back)) }
    Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAdjust) { Text(stringResource(R.string.inventory_adjust_stock)) }
        Button(onClick = onCount) { Text(stringResource(R.string.inventory_stock_count)) }
    }
    Text(
        stringResource(R.string.inventory_value_card, summary.totalValue.toPlainString()),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(R.string.inventory_items_card, summary.itemCount))
    Text(stringResource(R.string.inventory_low_card, summary.lowStockCount))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Text(stringResource(R.string.inventory_filter_status), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onStatusFilter(null) }, enabled = statusFilter != null) {
            Text(stringResource(R.string.inventory_filter_all))
        }
        StockStatus.entries.forEach { status ->
            Button(onClick = { onStatusFilter(status) }, enabled = statusFilter != status) {
                Text(stringResource(statusLabel(status)))
            }
        }
    }
    Text(stringResource(R.string.inventory_filter_warehouse), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onWarehouseFilter(null) }, enabled = warehouseFilter != null) {
            Text(stringResource(R.string.inventory_filter_all))
        }
        warehouses.forEach { warehouse ->
            Button(onClick = { onWarehouseFilter(warehouse.id) }, enabled = warehouseFilter != warehouse.id) {
                Text(warehouse.name)
            }
        }
    }
    if (categories.isNotEmpty()) {
        Text(stringResource(R.string.inventory_filter_category), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCategoryFilter(null) }, enabled = categoryFilter != null) {
                Text(stringResource(R.string.inventory_filter_all))
            }
            categories.forEach { category ->
                Button(onClick = { onCategoryFilter(category.id) }, enabled = categoryFilter != category.id) {
                    Text(category.name)
                }
            }
        }
    }
    if (brands.isNotEmpty()) {
        Text(stringResource(R.string.inventory_filter_brand), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onBrandFilter(null) }, enabled = brandFilter != null) {
                Text(stringResource(R.string.inventory_filter_all))
            }
            brands.forEach { brand ->
                Button(onClick = { onBrandFilter(brand.id) }, enabled = brandFilter != brand.id) {
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
                onClick = { onSelect(row.item.id) },
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
}

@Composable
private fun ColumnScope.ItemDetail(
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

@Composable
private fun ColumnScope.StockAdjustmentMode(
    items: List<ItemStockOption>,
    warehouses: List<NamedOption>,
    saving: Boolean,
    onDone: () -> Unit,
    onPost: (StockAdjustmentFormState, (AdjustmentResult) -> Unit) -> Unit,
) {
    var form by remember { mutableStateOf(StockAdjustmentFormState()) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val postedText = stringResource(R.string.inventory_adjust_posted)
    val warningText = stringResource(R.string.inventory_adjust_warning)
    val blockedText = stringResource(R.string.inventory_adjust_blocked)

    Button(onClick = onDone) { Text(stringResource(R.string.inventory_back_to_list)) }
    Text(stringResource(R.string.inventory_adjust_title), style = MaterialTheme.typography.headlineSmall)
    resultText?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    // A vertically scrolling Column, not a LazyColumn: every node
    // stays composed, so the test can scroll to the fields and
    // type into them — LazyColumn drops below-fold items from the
    // semantics tree, which is what broke the adjust-flow test.
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.inventory_field_item), style = MaterialTheme.typography.titleSmall)
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { form = form.copy(itemId = item.id) },
            ) {
                RadioButton(
                    selected = form.itemId == item.id,
                    onClick = { form = form.copy(itemId = item.id) },
                )
                Text(item.name)
            }
        }
        Text(stringResource(R.string.inventory_field_kind), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StockAdjustmentKind.entries.forEach { kind ->
                Button(
                    onClick = { form = form.copy(kind = kind) },
                    enabled = form.kind != kind,
                ) {
                    Text(stringResource(kindLabel(kind)))
                }
            }
        }
        if (form.kind == StockAdjustmentKind.MANUAL_CORRECTION) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { form = form.copy(manualInbound = true) },
                    enabled = !form.manualInbound,
                ) { Text(stringResource(R.string.inventory_direction_in)) }
                Button(
                    onClick = { form = form.copy(manualInbound = false) },
                    enabled = form.manualInbound,
                ) { Text(stringResource(R.string.inventory_direction_out)) }
            }
        }
        OutlinedTextField(
            value = form.quantityText,
            onValueChange = { form = form.copy(quantityText = it) },
            label = { Text(stringResource(R.string.inventory_field_quantity)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.date,
            onValueChange = { form = form.copy(date = it) },
            label = { Text(stringResource(R.string.inventory_field_date)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.reason,
            onValueChange = { form = form.copy(reason = it) },
            label = { Text(stringResource(R.string.inventory_field_reason)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.attachmentUri ?: "",
            onValueChange = { form = form.copy(attachmentUri = it.takeIf { uri -> uri.isNotBlank() }) },
            label = { Text(stringResource(R.string.inventory_field_attachment)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.inventory_field_warehouse), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = form.warehouseId == null,
                onClick = { form = form.copy(warehouseId = null) },
            )
            Text(stringResource(R.string.inventory_field_none))
        }
        warehouses.forEach { warehouse ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { form = form.copy(warehouseId = warehouse.id) },
            ) {
                RadioButton(
                    selected = form.warehouseId == warehouse.id,
                    onClick = { form = form.copy(warehouseId = warehouse.id) },
                )
                Text(warehouse.name)
            }
        }
    }

    // Post button outside the LazyColumn (Phase 18.4 lesson).
    Button(
        onClick = {
            onPost(form) { result ->
                resultText = when (result) {
                    is AdjustmentResult.Posted -> if (result.warning) warningText else postedText
                    AdjustmentResult.BlockedByPolicy -> blockedText
                }
                if (result is AdjustmentResult.Posted) {
                    form = StockAdjustmentFormState()
                }
            }
        },
        enabled = !saving && form.isValid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.inventory_adjust_save))
    }
}

@Composable
private fun ColumnScope.StockCountMode(
    rows: List<InventoryRow>,
    saving: Boolean,
    onDone: () -> Unit,
    onAdjust: (String, BigDecimal, String, (AdjustmentResult?) -> Unit) -> Unit,
) {
    val defaultReason = stringResource(R.string.inventory_count_default_reason)
    var reason by remember(defaultReason) { mutableStateOf(defaultReason) }
    var countedByItem by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    val postedText = stringResource(R.string.inventory_adjust_posted)
    val warningText = stringResource(R.string.inventory_adjust_warning)
    val blockedText = stringResource(R.string.inventory_adjust_blocked)

    Button(onClick = onDone) { Text(stringResource(R.string.inventory_back_to_list)) }
    Text(stringResource(R.string.inventory_count_title), style = MaterialTheme.typography.headlineSmall)
    lastResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    OutlinedTextField(
        value = reason,
        onValueChange = { reason = it },
        label = { Text(stringResource(R.string.inventory_count_reason)) },
        modifier = Modifier.fillMaxWidth(),
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.item.id }) { row ->
            val countRow = StockCountRow(
                item = row.item,
                expected = row.balance,
                countedText = countedByItem[row.item.id] ?: "",
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.item.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.inventory_count_expected, row.balance.toPlainString()))
                OutlinedTextField(
                    value = countRow.countedText,
                    onValueChange = { countedByItem = countedByItem + (row.item.id to it) },
                    label = { Text(stringResource(R.string.inventory_count_counted)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                countRow.variance?.let { variance ->
                    Text(stringResource(R.string.inventory_count_variance, variance.toPlainString()))
                }
                if (countRow.varianceMovementType != null) {
                    Button(
                        onClick = {
                            onAdjust(row.item.id, countRow.variance!!, reason) { result ->
                                lastResult = when (result) {
                                    is AdjustmentResult.Posted ->
                                        if (result.warning) warningText else postedText
                                    AdjustmentResult.BlockedByPolicy -> blockedText
                                    null -> postedText
                                }
                                if (result is AdjustmentResult.Posted) {
                                    countedByItem = countedByItem - row.item.id
                                }
                            }
                        },
                        enabled = !saving && reason.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.inventory_count_adjust))
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: StockStatus): Int = when (status) {
    StockStatus.IN_STOCK -> R.string.inventory_status_in_stock
    StockStatus.LOW_STOCK -> R.string.inventory_status_low_stock
    StockStatus.OUT_OF_STOCK -> R.string.inventory_status_out_of_stock
    StockStatus.OVERSTOCK -> R.string.inventory_status_overstock
    StockStatus.INACTIVE -> R.string.inventory_status_inactive
}

private fun kindLabel(kind: StockAdjustmentKind): Int = when (kind) {
    StockAdjustmentKind.OPENING_STOCK -> R.string.inventory_kind_opening
    StockAdjustmentKind.STOCK_IN -> R.string.inventory_kind_stock_in
    StockAdjustmentKind.STOCK_OUT -> R.string.inventory_kind_stock_out
    StockAdjustmentKind.DAMAGE -> R.string.inventory_kind_damage
    StockAdjustmentKind.EXPIRED -> R.string.inventory_kind_expired
    StockAdjustmentKind.LOST -> R.string.inventory_kind_lost
    StockAdjustmentKind.FOUND -> R.string.inventory_kind_found
    StockAdjustmentKind.MANUAL_CORRECTION -> R.string.inventory_kind_manual
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
