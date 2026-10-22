package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Offline-first customer entry and listing, always scoped to the active business. */
@Composable
fun PartiesScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: PartiesViewModel = hiltViewModel(),
) {
    val records by remember(viewModel, scope) { viewModel.observe(scope) }
        .collectAsState(initial = emptyList())
    var description by remember(scope) { mutableStateOf("") }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var deletingId by remember(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.parties_error)
    val deleteError = stringResource(R.string.parties_delete_error)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.parties_back)) }
        Text(stringResource(R.string.parties_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.parties_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = description.isNotBlank() && !saving,
            onClick = {
                val value = description.trim()
                saving = true
                coroutineScope.launch {
                    try {
                        val id = editingId ?: UUID.randomUUID().toString()
                        val existing = records.firstOrNull { it.id == id }
                        val payload = existing?.let { JSONObject(it.json) } ?: JSONObject().put("uuid", id)
                        payload.put("name", value)
                        viewModel.save(scope, id, payload.toString())
                        editingId = null
                        description = ""
                        error = null
                    } catch (_: Exception) {
                        error = saveError
                    } finally {
                        saving = false
                    }
                }
            },
        ) { Text(if (saving) stringResource(R.string.parties_saving) else stringResource(R.string.parties_save)) }
        if (editingId != null) {
            Button(onClick = { editingId = null; description = "" }, enabled = !saving) {
                Text(stringResource(R.string.parties_cancel_edit))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (records.isEmpty()) {
            Text(stringResource(R.string.parties_empty))
        }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                val displayName = runCatching {
                    JSONObject(record.json).optString("name", record.id)
                }.getOrDefault(record.id)
                Button(onClick = { editingId = record.id; description = displayName }, enabled = !saving) {
                    Text(stringResource(R.string.parties_edit, displayName))
                }
                Button(onClick = { deletingId = record.id }, enabled = !saving) {
                    Text(stringResource(R.string.parties_delete, displayName))
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text(stringResource(R.string.parties_delete_confirm)) },
            text = { Text(stringResource(R.string.parties_delete_warning)) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    coroutineScope.launch {
                        try {
                            viewModel.delete(scope, id)
                            if (editingId == id) { editingId = null; description = "" }
                            deletingId = null
                            error = null
                        } catch (_: Exception) {
                            error = deleteError
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.parties_delete_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deletingId = null }) {
                    Text(stringResource(R.string.parties_cancel))
                }
            },
        )
    }
}
