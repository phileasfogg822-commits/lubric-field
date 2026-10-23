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
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Offline-first item master data (spec §20/§21/§26), always scoped to
 * the active business. Modes: the item list (name, type, category,
 * brand, sale rate, opening stock), the full §20 form (with §23/§24
 * category and brand pickers), and the category/brand management
 * modes in ItemCatalog.kt. All reads/writes go through the
 * ViewModel's typed surface; the store stays the Phase 12 "products"
 * table with its tombstone delete semantics. Opening stock is a
 * stored quantity only — the inventory ledger is Phase 19.
 */
@Composable
fun ItemsScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: ItemsViewModel = hiltViewModel(),
) {
    val items by remember(viewModel, scope) { viewModel.observeItems(scope) }
        .collectAsState(initial = emptyList())
    val categories by remember(viewModel, scope) { viewModel.observeCategories(scope) }
        .collectAsState(initial = emptyList())
    val brands by remember(viewModel, scope) { viewModel.observeBrands(scope) }
        .collectAsState(initial = emptyList())
    val form = remember(scope) { ItemFormState() }
    var formOpen by remember(scope) { mutableStateOf(false) }
    var categoriesOpen by remember(scope) { mutableStateOf(false) }
    var brandsOpen by remember(scope) { mutableStateOf(false) }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.items_save_error)
    val deleteError = stringResource(R.string.items_delete_error)

    if (formOpen) {
        ItemForm(
            form = form,
            categories = categories,
            brands = brands,
            isEditing = editingId != null,
            saving = saving,
            formError = formError,
            saveError = error,
            onSave = {
                val item = form.toItem(editingId ?: UUID.randomUUID().toString())
                if (item == null) {
                    formError = true
                } else {
                    formError = false
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveItem(scope, item)
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
    } else if (categoriesOpen) {
        ItemCategoriesMode(
            scope = scope,
            items = items,
            categories = categories,
            viewModel = viewModel,
            onBack = { categoriesOpen = false },
        )
    } else if (brandsOpen) {
        ItemBrandsMode(
            scope = scope,
            items = items,
            brands = brands,
            viewModel = viewModel,
            onBack = { brandsOpen = false },
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_title), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !saving,
                    onClick = { form.reset(); editingId = null; formError = false; error = null; formOpen = true },
                ) { Text(stringResource(R.string.items_add)) }
                Button(
                    enabled = !saving,
                    onClick = { categoriesOpen = true },
                ) { Text(stringResource(R.string.items_categories)) }
                Button(
                    enabled = !saving,
                    onClick = { brandsOpen = true },
                ) { Text(stringResource(R.string.items_brands)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (items.isEmpty()) {
                Text(stringResource(R.string.items_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(item.type.labelRes()), style = MaterialTheme.typography.bodyMedium)
                        item.categoryId?.let { categoryId ->
                            Text(
                                stringResource(
                                    R.string.items_category_line,
                                    categories.firstOrNull { it.id == categoryId }?.name ?: "—",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        item.brandId?.let { brandId ->
                            Text(
                                stringResource(
                                    R.string.items_brand_line,
                                    brands.firstOrNull { it.id == brandId }?.name ?: "—",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        item.saleRate?.let { rate ->
                            Text(
                                stringResource(R.string.items_sale_rate_line, rate.toPlainString()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        item.openingStock?.let { stock ->
                            Text(
                                stringResource(R.string.items_opening_stock_line, stock.toPlainString()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (item.mainImageUri != null) {
                            Text(
                                stringResource(R.string.items_image_attached),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = { form.load(item); editingId = item.id; formError = false; error = null; formOpen = true },
                            ) { Text(stringResource(R.string.items_edit, item.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingId = item.id },
                            ) { Text(stringResource(R.string.items_delete, item.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.items_delete_confirm)) },
            text = { Text(stringResource(R.string.items_delete_warning)) },
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
                }) { Text(stringResource(R.string.items_delete_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
}

/** The full §20 field set as one scrollable form. */
@Composable
private fun ItemForm(
    form: ItemFormState,
    categories: List<ItemCategory>,
    brands: List<Brand>,
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
            stringResource(if (isEditing) R.string.items_edit_title else R.string.items_new_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        ItemTextField(form.name, { form.name = it }, R.string.items_name)
        Text(stringResource(R.string.items_type), style = MaterialTheme.typography.titleMedium)
        ItemType.entries.forEach { type ->
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
        Text(stringResource(R.string.items_category), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().selectable(
                selected = form.categoryId == null,
                onClick = { form.categoryId = null },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = form.categoryId == null, onClick = { form.categoryId = null })
            Text(stringResource(R.string.items_category_none))
        }
        categories.forEach { category ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = form.categoryId == category.id,
                    onClick = { form.categoryId = category.id },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = form.categoryId == category.id, onClick = { form.categoryId = category.id })
                Text(category.name)
            }
        }
        Text(stringResource(R.string.items_brand), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().selectable(
                selected = form.brandId == null,
                onClick = { form.brandId = null },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = form.brandId == null, onClick = { form.brandId = null })
            Text(stringResource(R.string.items_brand_none))
        }
        brands.forEach { brand ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = form.brandId == brand.id,
                    onClick = { form.brandId = brand.id },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = form.brandId == brand.id, onClick = { form.brandId = brand.id })
                Text(brand.name)
            }
        }
        ItemTextField(form.sku, { form.sku = it }, R.string.items_sku)
        ItemTextField(form.barcode, { form.barcode = it }, R.string.items_barcode)
        ItemTextField(form.unit, { form.unit = it }, R.string.items_unit)
        ItemTextField(form.purchaseRate, { form.purchaseRate = it }, R.string.items_purchase_rate, KeyboardType.Decimal)
        ItemTextField(form.saleRate, { form.saleRate = it }, R.string.items_sale_rate, KeyboardType.Decimal)
        ItemTextField(form.wholesaleRate, { form.wholesaleRate = it }, R.string.items_wholesale_rate, KeyboardType.Decimal)
        ItemTextField(form.dealerRate, { form.dealerRate = it }, R.string.items_dealer_rate, KeyboardType.Decimal)
        ItemTextField(form.minimumRate, { form.minimumRate = it }, R.string.items_minimum_rate, KeyboardType.Decimal)
        ItemTextField(form.mrp, { form.mrp = it }, R.string.items_mrp, KeyboardType.Decimal)
        ItemTextField(form.taxPercent, { form.taxPercent = it }, R.string.items_tax_percent, KeyboardType.Decimal)
        ItemTextField(form.openingStock, { form.openingStock = it }, R.string.items_opening_stock, KeyboardType.Decimal)
        ItemTextField(form.description, { form.description = it }, R.string.items_description)
        ItemTextField(form.mainImageUri, { form.mainImageUri = it }, R.string.items_main_image)
        ItemTextField(form.additionalImagesText, { form.additionalImagesText = it }, R.string.items_additional_images)
        if (formError) {
            Text(stringResource(R.string.items_form_error), color = MaterialTheme.colorScheme.error)
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = form.name.isNotBlank() && !saving, onClick = onSave) {
            Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save))
        }
        Button(enabled = !saving, onClick = onCancel) {
            Text(stringResource(R.string.items_cancel))
        }
    }
}

@Composable
private fun ItemTextField(
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

fun ItemType.labelRes(): Int = when (this) {
    ItemType.PRODUCT -> R.string.items_type_product
    ItemType.SERVICE -> R.string.items_type_service
    ItemType.COMBO -> R.string.items_type_combo
    ItemType.BUNDLE -> R.string.items_type_bundle
    ItemType.MANUFACTURED -> R.string.items_type_manufactured
    ItemType.EXPENSE -> R.string.items_type_expense
}
