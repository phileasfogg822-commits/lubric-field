package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Phase 12.1.6a: offline-first item entry backed by the scoped repository. */
@Composable
fun ItemsScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: ItemsViewModel = hiltViewModel(),
) {
    val records by remember(viewModel, scope) { viewModel.observe(scope) }
        .collectAsState(initial = emptyList())
    var name by remember(scope) { mutableStateOf("") }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.items_save_error)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.items_back)) }
        Text(stringResource(R.string.items_title))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.items_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = name.isNotBlank() && !saving,
            onClick = {
                val itemName = name.trim()
                coroutineScope.launch {
                    saving = true
                    try {
                        val id = editingId ?: UUID.randomUUID().toString()
                        val existing = records.firstOrNull { it.id == id }
                        val payload = existing?.let { JSONObject(it.json) } ?: JSONObject().put("uuid", id)
                        payload.put("name", itemName)
                        viewModel.save(scope, id, payload.toString())
                        name = ""
                        editingId = null
                        error = null
                    } catch (_: Exception) {
                        error = saveError
                    } finally {
                        saving = false
                    }
                }
            },
        ) { Text(stringResource(if (editingId == null) R.string.items_save else R.string.items_update)) }
        if (editingId != null) {
            Button(onClick = { editingId = null; name = "" }, enabled = !saving) {
                Text(stringResource(R.string.items_cancel_edit))
            }
        }
        error?.let { Text(it) }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                val displayName = runCatching { JSONObject(record.json).optString("name", record.id) }.getOrDefault(record.id)
                Button(onClick = { editingId = record.id; name = displayName }, enabled = !saving) {
                    Text(stringResource(R.string.items_edit, displayName))
                }
            }
        }
    }
}
