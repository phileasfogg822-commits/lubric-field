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
 * Phase 15.3 — category management (§23): list with item counts and
 * parent lines, the category form with a parent picker, and the same
 * tombstone delete flow as items. Deleting a category never edits
 * its items or subcategories — their links stay and render
 * unresolved ("—").
 */
@Composable
fun ItemCategoriesMode(
    scope: OperationalScope,
    items: List<Item>,
    categories: List<ItemCategory>,
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val form = remember(scope) { ItemCategoryFormState() }
    var formOpen by remember(scope) { mutableStateOf(false) }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.items_category_error)
    val deleteError = stringResource(R.string.items_category_delete_error)

    if (formOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingId != null) R.string.items_edit_category else R.string.items_new_category),
                style = MaterialTheme.typography.headlineMedium,
            )
            CatalogTextField(form.name, { form.name = it }, R.string.items_category_name)
            Text(stringResource(R.string.items_parent), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = form.parentId == null,
                    onClick = { form.parentId = null },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = form.parentId == null, onClick = { form.parentId = null })
                Text(stringResource(R.string.items_parent_none))
            }
            categories.filter { it.id != editingId }.forEach { parent ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = form.parentId == parent.id,
                        onClick = { form.parentId = parent.id },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = form.parentId == parent.id, onClick = { form.parentId = parent.id })
                    Text(parent.name)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = form.name.isNotBlank() && !saving,
                onClick = {
                    val category = form.toCategory(editingId ?: UUID.randomUUID().toString())
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveCategory(scope, category)
                            formOpen = false
                            editingId = null
                            error = null
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_category)) }
            Button(enabled = !saving, onClick = { formOpen = false; editingId = null; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_categories_title), style = MaterialTheme.typography.headlineMedium)
            Button(
                enabled = !saving,
                onClick = { form.reset(); editingId = null; error = null; formOpen = true },
            ) { Text(stringResource(R.string.items_add_category)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (categories.isEmpty()) {
                Text(stringResource(R.string.items_categories_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.items_category_items, items.count { it.categoryId == category.id }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        category.parentId?.let { parentId ->
                            Text(
                                stringResource(
                                    R.string.items_parent_line,
                                    categories.firstOrNull { it.id == parentId }?.name ?: "—",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = { form.load(category); editingId = category.id; error = null; formOpen = true },
                            ) { Text(stringResource(R.string.items_edit, category.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingId = category.id },
                            ) { Text(stringResource(R.string.items_delete, category.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.items_delete_category_confirm)) },
            text = { Text(stringResource(R.string.items_delete_category_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteCategory(scope, id)
                            deletingId = null
                            error = null
                        } catch (_: Exception) {
                            error = deleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_category_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingId = null }) {
                    Text(stringResource(R.string.items_cancel))
                }
            },
        )
    }
}

/**
 * Phase 15.3 — brand management (§24): list with item counts, the
 * brand form, and the same tombstone delete flow as items. Deleting
 * a brand never edits its items — their links stay and render
 * unresolved ("—").
 */
@Composable
fun ItemBrandsMode(
    scope: OperationalScope,
    items: List<Item>,
    brands: List<Brand>,
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val form = remember(scope) { BrandFormState() }
    var formOpen by remember(scope) { mutableStateOf(false) }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.items_brand_error)
    val deleteError = stringResource(R.string.items_brand_delete_error)

    if (formOpen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editingId != null) R.string.items_edit_brand else R.string.items_new_brand),
                style = MaterialTheme.typography.headlineMedium,
            )
            CatalogTextField(form.name, { form.name = it }, R.string.items_brand_name)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = form.name.isNotBlank() && !saving,
                onClick = {
                    val brand = form.toBrand(editingId ?: UUID.randomUUID().toString())
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveBrand(scope, brand)
                            formOpen = false
                            editingId = null
                            error = null
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) stringResource(R.string.items_saving) else stringResource(R.string.items_save_brand)) }
            Button(enabled = !saving, onClick = { formOpen = false; editingId = null; error = null }) {
                Text(stringResource(R.string.items_cancel))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
            Text(stringResource(R.string.items_brands_title), style = MaterialTheme.typography.headlineMedium)
            Button(
                enabled = !saving,
                onClick = { form.reset(); editingId = null; error = null; formOpen = true },
            ) { Text(stringResource(R.string.items_add_brand)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (brands.isEmpty()) {
                Text(stringResource(R.string.items_brands_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(brands, key = { it.id }) { brand ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(brand.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.items_brand_items, items.count { it.brandId == brand.id }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !saving,
                                onClick = { form.load(brand); editingId = brand.id; error = null; formOpen = true },
                            ) { Text(stringResource(R.string.items_edit, brand.name)) }
                            Button(
                                enabled = !saving,
                                onClick = { deletingId = brand.id },
                            ) { Text(stringResource(R.string.items_delete, brand.name)) }
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.items_delete_brand_confirm)) },
            text = { Text(stringResource(R.string.items_delete_brand_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.deleteBrand(scope, id)
                            deletingId = null
                            error = null
                        } catch (_: Exception) {
                            error = deleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.items_delete_brand_confirm)) }
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
private fun CatalogTextField(
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
