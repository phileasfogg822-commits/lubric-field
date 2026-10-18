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
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text("Back") }
        Text("Items")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = name.isNotBlank(),
            onClick = {
                val itemName = name.trim()
                coroutineScope.launch {
                    try {
                        val id = UUID.randomUUID().toString()
                        viewModel.save(scope, id, JSONObject().put("uuid", id).put("name", itemName).toString())
                        name = ""
                        error = null
                    } catch (_: Exception) {
                        error = "Unable to save item locally"
                    }
                }
            },
        ) { Text("Save item offline") }
        error?.let { Text(it) }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                Text(runCatching { JSONObject(record.json).optString("name", record.id) }.getOrDefault(record.id))
            }
        }
    }
}
