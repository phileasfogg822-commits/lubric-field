package com.lubrcapp.lubrcapp.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.feature.notifications.NotificationCenterScreen
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.time.LocalDate

/**
 * Phase 29.5 — the notifications module's shell
 * content: the center over real stored records.
 * Only the overdue-invoice derivation is wired
 * today: low stock needs a stock-quantity
 * surface the shell does not have, and expiry,
 * purchase-due and credit inputs need their
 * record surfaces — all named follow-ups, not
 * fake rows. Mark-read state is in-memory only
 * (derived notifications are not stored), so it
 * resets when the content leaves composition.
 */
@Composable
fun NotificationsContent(scope: OperationalScope, onBack: () -> Unit) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    val documents by salesViewModel.observeDocuments(scope).collectAsState(initial = emptyList())
    val parties by salesViewModel.observeParties(scope).collectAsState(initial = emptyList())
    var readIds by remember { mutableStateOf(emptySet<String>()) }
    val today = LocalDate.now().toString()
    val notifications = remember(documents, parties, today, readIds) {
        val partyNames = parties.associate { it.id to it.name }
        NotificationsMapper.overdueInvoices(documents, partyNames, today).map { notification ->
            if (notification.id in readIds) notification.copy(isRead = true) else notification
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.data_back)) }
        }
        Box(modifier = Modifier.weight(1f)) {
            NotificationCenterScreen(
                notifications = notifications,
                onMarkRead = { id -> readIds = readIds + id },
            )
        }
    }
}
