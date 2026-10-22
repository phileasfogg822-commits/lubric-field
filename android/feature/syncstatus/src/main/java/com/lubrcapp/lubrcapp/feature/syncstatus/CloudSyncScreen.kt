package com.lubrcapp.lubrcapp.feature.syncstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.launch

/**
 * Opt-in cloud sync pairing. The token is write-only: it is sent to the
 * Keystore-backed credential store and never read back into the UI.
 */
@Composable
fun CloudSyncScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var endpoint by remember(scope) { mutableStateOf("") }
    var token by remember(scope) { mutableStateOf("") }
    var working by remember(scope) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scope) { viewModel.refresh(scope) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.cloud_sync_back)) }
        Text(
            stringResource(R.string.cloud_sync_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.paired) {
            Text(stringResource(R.string.cloud_sync_paired, state.endpoint ?: ""))
            Button(
                enabled = !working,
                onClick = {
                    working = true
                    coroutineScope.launch {
                        try {
                            viewModel.unpair(scope)
                        } finally {
                            working = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.cloud_sync_unpair)) }
        } else {
            Text(stringResource(R.string.cloud_sync_unpaired_note))
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text(stringResource(R.string.cloud_sync_endpoint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.cloud_sync_token)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                enabled = endpoint.isNotBlank() && token.isNotBlank() && !working,
                onClick = {
                    working = true
                    coroutineScope.launch {
                        try {
                            viewModel.pair(scope, endpoint, token)
                            token = ""
                        } finally {
                            working = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.cloud_sync_pair)) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
