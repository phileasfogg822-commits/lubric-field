package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.sales.DocumentStatusRules
import com.lubrcapp.lubrcapp.domain.sales.QuotationStatus
import com.lubrcapp.lubrcapp.domain.sales.SalesOrderStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Phase 18.4 — the typed quotation / sales-order surface that
 * replaces the Phase 12 raw-record screen: a document list, an
 * editor with line rows and a live totals preview (computed by
 * the Phase 17 engine through the 18.1 bridge), status actions
 * driven by the 18.2 rules, quotation → order conversion
 * (18.5), and the §14 timeline under each document.
 *
 * [currencyCode] defaults to the ISO neutral code XXX ("no
 * currency"): amounts throughout this app are currency-less
 * text until the shell passes the current business's currency
 * through the module registry — a named Phase 18 residual.
 */
@Composable
fun SalesScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    currencyCode: String = "XXX",
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val documents by remember(viewModel, scope) { viewModel.observeDocuments(scope) }
        .collectAsState(initial = emptyList())
    val parties by remember(viewModel, scope) { viewModel.observeParties(scope) }
        .collectAsState(initial = emptyList())
    val items by remember(viewModel, scope) { viewModel.observeItems(scope) }
        .collectAsState(initial = emptyList())
    var form by remember(scope) { mutableStateOf<SalesDocumentFormState?>(null) }
    var timeline by remember(scope) { mutableStateOf<List<TimelineEntry>>(emptyList()) }
    var saving by remember(scope) { mutableStateOf(false) }
    var error by remember(scope) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.sales_error)
    val currency = remember(currencyCode) { CurrencyCode.of(currencyCode) }

    fun openDocument(document: SalesDocument) {
        coroutineScope.launch {
            val lines = viewModel.loadLines(scope, document.id)
            timeline = viewModel.loadTimeline(scope, document.id)
            form = SalesDocumentFormState.fromDocument(document, lines)
            error = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.sales_back)) }
        Text(stringResource(R.string.sales_title), style = MaterialTheme.typography.headlineMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val currentForm = form
        if (currentForm == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    form = SalesDocumentFormState(
                        type = SalesDocumentType.QUOTATION,
                        lines = listOf(SalesLineDraft(id = UUID.randomUUID().toString())),
                    )
                }) { Text(stringResource(R.string.sales_new_quotation)) }
                Button(onClick = {
                    form = SalesDocumentFormState(
                        type = SalesDocumentType.SALES_ORDER,
                        lines = listOf(SalesLineDraft(id = UUID.randomUUID().toString())),
                    )
                }) { Text(stringResource(R.string.sales_new_order)) }
            }
            if (documents.isEmpty()) {
                Text(stringResource(R.string.sales_empty))
            }
            LazyColumn {
                items(documents, key = { it.id }) { document ->
                    Button(
                        onClick = { openDocument(document) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.sales_document_row,
                                document.number.ifBlank { stringResource(R.string.sales_unnumbered) },
                                stringResource(
                                    if (document.type == SalesDocumentType.QUOTATION) {
                                        R.string.sales_type_quotation
                                    } else {
                                        R.string.sales_type_order
                                    }
                                ),
                                document.status,
                                document.date,
                            )
                        )
                    }
                }
            }
        } else {
            DocumentEditor(
                form = currentForm,
                parties = parties,
                items = items,
                timeline = timeline,
                saving = saving,
                currency = currency,
                onFormChange = { form = it },
                onSave = {
                    // Inline: currentForm is smart-cast non-null here. Do NOT
                    // route through a local fun that re-reads the nullable
                    // `form` state — that indirection silently no-ops.
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveForm(scope, currentForm, currency)
                            form = null
                            timeline = emptyList()
                            error = null
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
                onCancel = { form = null; timeline = emptyList(); error = null },
                onStatusChange = { updated ->
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveDocument(scope, updated)
                            form = currentForm.copy(status = updated.status, number = updated.number)
                            error = null
                        } catch (_: Exception) {
                            error = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
                onConvert = {
                    val quotationId = currentForm.existingId ?: return@DocumentEditor
                    saving = true
                    coroutineScope.launch {
                        try {
                            val order = viewModel.convertQuotationToOrder(scope, quotationId)
                            viewModel.saveTimelineEntry(
                                scope,
                                TimelineEntry(
                                    id = UUID.randomUUID().toString(),
                                    documentId = quotationId,
                                    documentType = SalesDocumentType.QUOTATION.name,
                                    documentNumber = currentForm.number,
                                    partyId = currentForm.partyId,
                                    action = "CONVERTED",
                                    grandTotal = "",
                                    timestamp = Instant.now().toString(),
                                ),
                            )
                            form = null
                            timeline = emptyList()
                            error = null
                            openDocument(order)
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
private fun DocumentEditor(
    form: SalesDocumentFormState,
    parties: List<PartyOption>,
    items: List<ItemOption>,
    timeline: List<TimelineEntry>,
    saving: Boolean,
    currency: CurrencyCode,
    onFormChange: (SalesDocumentFormState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onStatusChange: (SalesDocument) -> Unit,
    onConvert: () -> Unit,
) {
    var pickerLineId by remember(form.existingId) { mutableStateOf<String?>(null) }
    val totals = remember(form, currency) { form.totalsOrNull(currency) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(
                    if (form.type == SalesDocumentType.QUOTATION) {
                        R.string.sales_type_quotation
                    } else {
                        R.string.sales_type_order
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (form.number.isNotBlank()) {
                Text(form.number, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.sales_status, form.status))
        }
        item {
            OutlinedTextField(
                value = form.date,
                onValueChange = { onFormChange(form.copy(date = it)) },
                label = { Text(stringResource(R.string.sales_date)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.dueDate,
                onValueChange = { onFormChange(form.copy(dueDate = it)) },
                label = { Text(stringResource(R.string.sales_due_date)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                label = { Text(stringResource(R.string.sales_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(stringResource(R.string.sales_party), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = form.partyId == null,
                    onClick = { onFormChange(form.copy(partyId = null)) },
                )
                Text(stringResource(R.string.sales_no_party))
            }
        }
        items(parties, key = { "party-" + it.id }) { party ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = form.partyId == party.id,
                    onClick = { onFormChange(form.copy(partyId = party.id)) },
                )
                Text(party.name)
            }
        }
        item {
            Text(stringResource(R.string.sales_lines), style = MaterialTheme.typography.titleMedium)
        }
        items(form.lines, key = { it.id }) { draft ->
            LineEditor(
                draft = draft,
                items = items,
                pickerOpen = pickerLineId == draft.id,
                onTogglePicker = { pickerLineId = if (pickerLineId == draft.id) null else draft.id },
                onChange = { updated ->
                    onFormChange(form.copy(lines = form.lines.map { if (it.id == draft.id) updated else it }))
                },
                onRemove = {
                    onFormChange(form.copy(lines = form.lines.filterNot { it.id == draft.id }))
                },
            )
        }
        item {
            Button(
                onClick = {
                    onFormChange(
                        form.copy(lines = form.lines + SalesLineDraft(id = UUID.randomUUID().toString()))
                    )
                },
                enabled = !saving,
            ) { Text(stringResource(R.string.sales_add_line)) }
            totals?.let {
                Text(
                    stringResource(R.string.sales_grand_total, it.grandTotal.amount.toPlainString()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = !saving && form.isValid) {
                    Text(if (saving) stringResource(R.string.sales_saving) else stringResource(R.string.sales_save))
                }
                Button(onClick = onCancel, enabled = !saving) {
                    Text(stringResource(R.string.sales_cancel_edit))
                }
            }
        }
        // Status actions + conversion, only for a saved document.
        if (form.existingId != null) {
            item {
                Text(stringResource(R.string.sales_actions), style = MaterialTheme.typography.titleMedium)
            }
            val document = form.toDocument(form.existingId)
            if (form.type == SalesDocumentType.QUOTATION) {
                val from = SalesDocumentStatus.quotationStatus(document)
                items(QuotationStatus.entries.filter { DocumentStatusRules.canTransition(from, it) }) { target ->
                    Button(
                        onClick = { onStatusChange(SalesDocumentStatus.transition(document, target)) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.sales_mark_status, target.name)) }
                }
            } else {
                val from = SalesDocumentStatus.orderStatus(document)
                items(SalesOrderStatus.entries.filter { DocumentStatusRules.canTransition(from, it) }) { target ->
                    Button(
                        onClick = { onStatusChange(SalesDocumentStatus.transition(document, target)) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.sales_mark_status, target.name)) }
                }
            }
            if (form.type == SalesDocumentType.QUOTATION &&
                SalesDocumentStatus.quotationStatus(document) == QuotationStatus.ACCEPTED
            ) {
                item {
                    Button(onClick = onConvert, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sales_convert_to_order))
                    }
                }
            }
            if (timeline.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.sales_timeline), style = MaterialTheme.typography.titleMedium)
                }
                items(timeline, key = { "tl-" + it.id }) { entry ->
                    Text(
                        stringResource(
                            R.string.sales_timeline_entry,
                            entry.timestamp,
                            entry.action,
                            entry.grandTotal,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LineEditor(
    draft: SalesLineDraft,
    items: List<ItemOption>,
    pickerOpen: Boolean,
    onTogglePicker: () -> Unit,
    onChange: (SalesLineDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            label = { Text(stringResource(R.string.sales_description)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.quantity,
                onValueChange = { onChange(draft.copy(quantity = it)) },
                label = { Text(stringResource(R.string.sales_quantity)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.rate,
                onValueChange = { onChange(draft.copy(rate = it)) },
                label = { Text(stringResource(R.string.sales_rate)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.taxName,
                onValueChange = { onChange(draft.copy(taxName = it)) },
                label = { Text(stringResource(R.string.sales_tax_name)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.taxPercent,
                onValueChange = { onChange(draft.copy(taxPercent = it)) },
                label = { Text(stringResource(R.string.sales_tax_percent)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onTogglePicker) {
                Text(stringResource(R.string.sales_pick_item))
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.sales_remove_line))
            }
        }
        if (pickerOpen) {
            items.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = draft.itemId == option.id,
                        onClick = {
                            onChange(
                                draft.copy(
                                    itemId = option.id,
                                    description = option.name,
                                    rate = option.saleRate?.toPlainString() ?: draft.rate,
                                    taxPercent = option.taxPercent?.toPlainString() ?: draft.taxPercent,
                                )
                            )
                        },
                    )
                    Text(option.name)
                }
            }
        }
    }
}
