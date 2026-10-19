package com.lubrcapp.lubrcapp.feature.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Offline-first purchase entry and listing, always scoped to the active business. */
@Composable
fun PurchasesScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: PurchasesViewModel = hiltViewModel(),
) {
    val records by remember(viewModel, scope) { viewModel.observe(scope) }
        .collectAsState(initial = emptyList())
    var description by remember(scope) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text("Back") }
        Text("Purchases", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Purchase") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = description.isNotBlank() && !saving,
            onClick = {
                val value = description.trim()
                saving = true
                coroutineScope.launch {
                    try {
                        val id = UUID.randomUUID().toString()
                        viewModel.save(scope, id, JSONObject().put("uuid", id).put("description", value).toString())
                        description = ""
                        error = null
                    } catch (_: Exception) {
                        error = "Unable to save purchase locally"
                    } finally {
                        saving = false
                    }
                }
            },
        ) { Text(if (saving) "Saving…" else "Save purchase") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                Text(runCatching {
                    JSONObject(record.json).optString("description", record.id)
                }.getOrDefault(record.id))
            }
        }
    }
}
