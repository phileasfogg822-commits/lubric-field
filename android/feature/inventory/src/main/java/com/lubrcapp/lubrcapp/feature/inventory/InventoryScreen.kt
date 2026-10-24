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
 * Phase 19.3/19.4/19.5/19.6 — the inventory module screen. List
 * mode is the §31 summary (computed cards, filters,
 * ledger-derived rows); an item's detail shows its movement
 * history, §33 levels editor, and §30 serial registry. Adjust
 * mode posts §36 adjustments, Count mode runs the §37 physical
 * count, and Transfers mode runs the §35 transfer flow — all
 * write through the ViewModel's §175-checked posting surface,
 * never directly.
 */
private enum class InventoryMode { LIST, ADJUST, COUNT, TRANSFERS }

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
    val transfers by remember(viewModel, scope) { viewModel.observeTransfers(scope) }
        .collectAsState(initial = emptyList())
    val serials by remember(viewModel, scope) { viewModel.observeSerials(scope) }
        .collectAsState(initial = emptyList())
    val serialWriteoffReason = stringResource(R.string.inventory_serial_writeoff_reason)

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
            InventoryMode.TRANSFERS -> StockTransfersMode(
                items = items,
                warehouses = warehouses,
                transfers = transfers,
                saving = saving,
                onDone = { mode = InventoryMode.LIST },
                onCreate = { form, onResult ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            onResult(viewModel.createTransfer(scope, form))
                        } catch (_: Exception) {
                            error = saveError
                            onResult(null)
                        } finally {
                            saving = false
                        }
                    }
                },
                onDispatch = { transferId, onResult ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            onResult(viewModel.dispatchTransfer(scope, transferId))
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
                onReceive = { transferId, onResult ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            onResult(viewModel.receiveTransfer(scope, transferId))
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
                onCancel = { transferId, onResult ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            onResult(viewModel.cancelTransfer(scope, transferId))
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
                        onTransfers = { mode = InventoryMode.TRANSFERS },
                    )
                } else {
                    ItemDetail(
                        row = selectedRow,
                        movements = movements.filter { it.itemId == selectedRow.item.id },
                        levels = levels[selectedRow.item.id],
                        serials = serials.filter { it.itemId == selectedRow.item.id },
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
                        onAddSerial = { serialNumber, onResult ->
                            saving = true
                            coroutineScope.launch {
                                try {
                                    onResult(viewModel.addSerial(scope, selectedRow.item.id, serialNumber))
                                } catch (_: Exception) {
                                    error = saveError
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        onRemoveSerial = { serialId ->
                            saving = true
                            coroutineScope.launch {
                                try {
                                    viewModel.removeSerial(scope, serialId, serialWriteoffReason)
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
    onTransfers: () -> Unit,
) {
    Button(onClick = onBack) { Text(stringResource(R.string.inventory_back)) }
    Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAdjust) { Text(stringResource(R.string.inventory_adjust_stock)) }
        Button(onClick = onCount) { Text(stringResource(R.string.inventory_stock_count)) }
        Button(onClick = onTransfers) { Text(stringResource(R.string.inventory_transfers)) }
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
    serials: List<StockSerialRecord>,
    saving: Boolean,
    error: String?,
    onBackToList: () -> Unit,
    onSaveLevels: (StockLevelsRecord) -> Unit,
    onAddSerial: (String, (SerialAddResult) -> Unit) -> Unit,
    onRemoveSerial: (String) -> Unit,
) {
    var minimumText by remember(row.item.id, levels) { mutableStateOf(levels?.minimum?.toPlainString() ?: "") }
    var reorderText by remember(row.item.id, levels) { mutableStateOf(levels?.reorderLevel?.toPlainString() ?: "") }
    var maximumText by remember(row.item.id, levels) { mutableStateOf(levels?.maximum?.toPlainString() ?: "") }
    var serialText by remember(row.item.id) { mutableStateOf("") }
    var serialMessage by remember(row.item.id) { mutableStateOf<String?>(null) }
    val serialAddedText = stringResource(R.string.inventory_serial_added)
    val serialDuplicateText = stringResource(R.string.inventory_serial_duplicate)
    val serialInvalidText = stringResource(R.string.inventory_serial_invalid)
    val inStockSerials = serials.filter { it.status == SerialStatus.IN_STOCK }

    // A vertically scrolling Column, not a fixed Column with an
    // inner LazyColumn: the 19.6 additions made the fixed content
    // tall enough to squeeze the history list to zero height on
    // small screens (nodes present in the tree but not displayed).
    // Every row stays composed here, so the test can always find
    // and interact with the fields.
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            movements.forEach { movement ->
                Text(
                    stringResource(
                        R.string.inventory_history_row,
                        movement.date,
                        movement.type.name,
                        movement.quantity.toPlainString(),
                    )
                )
            }
            // §30 serials ride in the same scrolling area as
            // display rows — their Remove buttons act on the row's
            // own immutable id only; the stateful Add form stays in
            // the fixed area below (the Phase 18.4 lesson).
            Text(stringResource(R.string.inventory_serials_title), style = MaterialTheme.typography.titleMedium)
            if (inStockSerials.isEmpty()) {
                Text(stringResource(R.string.inventory_serial_empty))
            }
            inStockSerials.forEach { serial ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(serial.serialNumber)
                    Button(onClick = { onRemoveSerial(serial.id) }, enabled = !saving) {
                        Text(stringResource(R.string.inventory_serial_remove))
                    }
                }
            }
        }

        // §33 levels editor — every node stays composed in the
        // scrolling Column, so its Save button always carries the
        // current handler (the Phase 18.4 lesson).
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

        // §30 receive flow — fixed area, like the levels Save.
        serialMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedTextField(
            value = serialText,
            onValueChange = { serialText = it },
            label = { Text(stringResource(R.string.inventory_serial_field)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onAddSerial(serialText) { result ->
                    serialMessage = when (result) {
                        is SerialAddResult.Added -> serialAddedText
                        SerialAddResult.Duplicate -> serialDuplicateText
                        SerialAddResult.Invalid -> serialInvalidText
                    }
                    if (result is SerialAddResult.Added) {
                        serialText = ""
                    }
                }
            },
            enabled = !saving && serialText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.inventory_serial_add))
        }
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

@Composable
private fun ColumnScope.StockTransfersMode(
    items: List<ItemStockOption>,
    warehouses: List<NamedOption>,
    transfers: List<StockTransferRecord>,
    saving: Boolean,
    onDone: () -> Unit,
    onCreate: (StockTransferFormState, (StockTransferRecord?) -> Unit) -> Unit,
    onDispatch: (String, (TransferResult) -> Unit) -> Unit,
    onReceive: (String, (TransferResult) -> Unit) -> Unit,
    onCancel: (String, (TransferResult) -> Unit) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val selected = transfers.firstOrNull { it.id == selectedId }

    when {
        editing -> TransferEditor(
            items = items,
            warehouses = warehouses,
            saving = saving,
            onBack = { editing = false },
            onCreate = { form, onResult ->
                onCreate(form) { created ->
                    if (created != null) {
                        editing = false
                        selectedId = created.id
                    }
                    onResult(created)
                }
            },
        )
        selected != null -> TransferDetail(
            transfer = selected,
            items = items,
            warehouses = warehouses,
            saving = saving,
            onBack = { selectedId = null },
            onDispatch = onDispatch,
            onReceive = onReceive,
            onCancel = onCancel,
        )
        else -> {
            Button(onClick = onDone) { Text(stringResource(R.string.inventory_back_to_list)) }
            Text(stringResource(R.string.inventory_transfer_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { editing = true }) { Text(stringResource(R.string.inventory_transfer_new)) }
            if (transfers.isEmpty()) {
                Text(stringResource(R.string.inventory_transfer_empty))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(transfers, key = { it.id }) { transfer ->
                    Button(
                        onClick = { selectedId = transfer.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.inventory_transfer_row,
                                warehouseName(warehouses, transfer.fromWarehouseId),
                                warehouseName(warehouses, transfer.toWarehouseId),
                                stringResource(transferStatusLabel(transfer.status)),
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TransferEditor(
    items: List<ItemStockOption>,
    warehouses: List<NamedOption>,
    saving: Boolean,
    onBack: () -> Unit,
    onCreate: (StockTransferFormState, (StockTransferRecord?) -> Unit) -> Unit,
) {
    var form by remember { mutableStateOf(StockTransferFormState()) }

    Button(onClick = onBack) { Text(stringResource(R.string.inventory_back_to_list)) }
    Text(stringResource(R.string.inventory_transfer_new), style = MaterialTheme.typography.headlineSmall)

    // A vertically scrolling Column for the same reason as
    // StockAdjustmentMode: the per-item quantity fields must stay
    // composed so they can be scrolled to and typed into.
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.inventory_transfer_from), style = MaterialTheme.typography.titleSmall)
        warehouses.forEach { warehouse ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { form = form.copy(fromWarehouseId = warehouse.id) },
            ) {
                RadioButton(
                    selected = form.fromWarehouseId == warehouse.id,
                    onClick = { form = form.copy(fromWarehouseId = warehouse.id) },
                )
                Text(warehouse.name)
            }
        }
        Text(stringResource(R.string.inventory_transfer_to), style = MaterialTheme.typography.titleSmall)
        warehouses.forEach { warehouse ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { form = form.copy(toWarehouseId = warehouse.id) },
            ) {
                RadioButton(
                    selected = form.toWarehouseId == warehouse.id,
                    onClick = { form = form.copy(toWarehouseId = warehouse.id) },
                )
                Text(warehouse.name)
            }
        }
        OutlinedTextField(
            value = form.date,
            onValueChange = { form = form.copy(date = it) },
            label = { Text(stringResource(R.string.inventory_field_date)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { form = form.copy(note = it) },
            label = { Text(stringResource(R.string.inventory_field_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.inventory_field_item), style = MaterialTheme.typography.titleSmall)
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = form.quantityByItem[item.id] ?: "",
                    onValueChange = { form = form.copy(quantityByItem = form.quantityByItem + (item.id to it)) },
                    label = { Text(stringResource(R.string.inventory_field_quantity)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Save button outside the scroll (Phase 18.4 lesson).
    Button(
        onClick = { onCreate(form) { } },
        enabled = !saving && form.isValid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.inventory_transfer_save))
    }
}

@Composable
private fun ColumnScope.TransferDetail(
    transfer: StockTransferRecord,
    items: List<ItemStockOption>,
    warehouses: List<NamedOption>,
    saving: Boolean,
    onBack: () -> Unit,
    onDispatch: (String, (TransferResult) -> Unit) -> Unit,
    onReceive: (String, (TransferResult) -> Unit) -> Unit,
    onCancel: (String, (TransferResult) -> Unit) -> Unit,
) {
    var resultText by remember(transfer.id) { mutableStateOf<String?>(null) }
    val dispatchedText = stringResource(R.string.inventory_transfer_dispatched)
    val receivedText = stringResource(R.string.inventory_transfer_received)
    val cancelledText = stringResource(R.string.inventory_transfer_cancelled)
    val warningText = stringResource(R.string.inventory_adjust_warning)
    val blockedText = stringResource(R.string.inventory_adjust_blocked)
    val invalidText = stringResource(R.string.inventory_transfer_invalid)

    Button(onClick = onBack) { Text(stringResource(R.string.inventory_back_to_list)) }
    Text(
        stringResource(
            R.string.inventory_transfer_row,
            warehouseName(warehouses, transfer.fromWarehouseId),
            warehouseName(warehouses, transfer.toWarehouseId),
            stringResource(transferStatusLabel(transfer.status)),
        ),
        style = MaterialTheme.typography.headlineSmall,
    )
    resultText?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(transfer.lines, key = { it.itemId }) { line ->
            Text(
                stringResource(
                    R.string.inventory_transfer_line,
                    items.firstOrNull { it.id == line.itemId }?.name ?: line.itemId,
                    line.quantity.toPlainString(),
                )
            )
        }
    }

    // Status actions outside the LazyColumn (Phase 18.4 lesson).
    when (transfer.status) {
        TransferStatus.DRAFT -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onDispatch(transfer.id) { result ->
                            resultText = when (result) {
                                is TransferResult.Done ->
                                    if (result.warning) warningText else dispatchedText
                                TransferResult.BlockedByPolicy -> blockedText
                                TransferResult.InvalidState -> invalidText
                            }
                        }
                    },
                    enabled = !saving,
                ) { Text(stringResource(R.string.inventory_transfer_dispatch)) }
                Button(
                    onClick = {
                        onCancel(transfer.id) { result ->
                            resultText = when (result) {
                                is TransferResult.Done -> cancelledText
                                TransferResult.BlockedByPolicy -> blockedText
                                TransferResult.InvalidState -> invalidText
                            }
                        }
                    },
                    enabled = !saving,
                ) { Text(stringResource(R.string.inventory_transfer_cancel)) }
            }
        }
        TransferStatus.DISPATCHED -> {
            Button(
                onClick = {
                    onReceive(transfer.id) { result ->
                        resultText = when (result) {
                            is TransferResult.Done -> receivedText
                            TransferResult.BlockedByPolicy -> blockedText
                            TransferResult.InvalidState -> invalidText
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.inventory_transfer_receive)) }
        }
        TransferStatus.RECEIVED, TransferStatus.CANCELLED -> Unit
    }
}

private fun warehouseName(warehouses: List<NamedOption>, id: String): String =
    warehouses.firstOrNull { it.id == id }?.name ?: id

private fun transferStatusLabel(status: TransferStatus): Int = when (status) {
    TransferStatus.DRAFT -> R.string.inventory_transfer_status_draft
    TransferStatus.DISPATCHED -> R.string.inventory_transfer_status_dispatched
    TransferStatus.RECEIVED -> R.string.inventory_transfer_status_received
    TransferStatus.CANCELLED -> R.string.inventory_transfer_status_cancelled
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
