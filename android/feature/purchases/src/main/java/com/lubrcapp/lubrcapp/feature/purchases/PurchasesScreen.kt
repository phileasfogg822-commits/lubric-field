package com.lubrcapp.lubrcapp.feature.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.domain.payment.PaymentDirection
import com.lubrcapp.lubrcapp.domain.common.PaymentId
import com.lubrcapp.lubrcapp.domain.common.PaymentMethodId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Offline-first purchase entry and listing, always scoped to the active business. */
@Composable
fun PurchasesScreen(
    scope: OperationalScope,
    onBack: () -> Unit,
    viewModel: PurchasesViewModel = hiltViewModel(),
    accountOptions: List<PurchaseAccountOption> = emptyList(),
    onPostPayment: (suspend (Payment, String, PurchasePaymentPostingAccounts) -> PurchasePostResult)? = null,
) {
    val records by remember(viewModel, scope) { viewModel.observe(scope) }
        .collectAsState(initial = emptyList())
    val payables by remember(viewModel, scope) { viewModel.observePayables(scope) }
        .collectAsState(initial = emptyList())
    var description by remember(scope) { mutableStateOf("") }
    var editingId by remember(scope) { mutableStateOf<String?>(null) }
    var saving by remember(scope) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember(scope) { mutableStateOf<String?>(null) }
    var selectedPayableId by remember(scope) { mutableStateOf<String?>(null) }
    var payAmount by remember(scope) { mutableStateOf("") }
    var payCashId by remember(scope) { mutableStateOf<String?>(null) }
    var payPayableId by remember(scope) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveError = stringResource(R.string.purchases_error)
    val payReceivedNotice = stringResource(R.string.purchases_pay_received)
    val payRejectedTemplate = stringResource(R.string.purchases_pay_rejected)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.purchases_back)) }
        Text(stringResource(R.string.purchases_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.purchases_label)) },
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
                        payload.put("description", value)
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
        ) { Text(if (saving) stringResource(R.string.purchases_saving) else stringResource(R.string.purchases_save)) }
        if (editingId != null) {
            Button(onClick = { editingId = null; description = "" }, enabled = !saving) {
                Text(stringResource(R.string.purchases_cancel_edit))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Phase 31.2 — pay a vendor payable. The section shows
        // only when the shell wires a payment handler; the list
        // refreshes itself through observePayables when
        // applyPayment saves the updated payable.
        val paymentHandler = onPostPayment
        if (paymentHandler != null) {
            Text(stringResource(R.string.purchases_payables_title), style = MaterialTheme.typography.titleMedium)
            if (payables.isEmpty()) {
                Text(stringResource(R.string.purchases_payables_empty))
            } else {
                payables.forEach { payable ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = selectedPayableId == payable.id,
                            onClick = { selectedPayableId = payable.id },
                            role = Role.RadioButton,
                        ),
                    ) {
                        RadioButton(
                            selected = selectedPayableId == payable.id,
                            onClick = null,
                        )
                        Text(
                            stringResource(
                                R.string.purchases_payable_row,
                                payable.documentNumber.ifBlank { payable.id },
                                payable.vendorId,
                                (payable.totalAmount - payable.paidAmount).toPlainString(),
                                payable.currency,
                            ),
                        )
                    }
                }
                val selected = payables.firstOrNull { it.id == selectedPayableId }
                if (selected != null) {
                    if (accountOptions.isEmpty()) {
                        Text(stringResource(R.string.purchases_no_accounts))
                    } else {
                        OutlinedTextField(
                            value = payAmount,
                            onValueChange = { payAmount = it },
                            label = { Text(stringResource(R.string.purchases_pay_amount)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("payAmount"),
                        )
                        Text(stringResource(R.string.purchases_pay_cash), style = MaterialTheme.typography.titleMedium)
                        accountOptions.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.selectable(
                                    selected = payCashId == option.id,
                                    onClick = { payCashId = option.id },
                                    role = Role.RadioButton,
                                ),
                            ) {
                                RadioButton(
                                    selected = payCashId == option.id,
                                    onClick = null,
                                )
                                Text(option.name)
                            }
                        }
                        Text(stringResource(R.string.purchases_pay_payable), style = MaterialTheme.typography.titleMedium)
                        accountOptions.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.selectable(
                                    selected = payPayableId == option.id,
                                    onClick = { payPayableId = option.id },
                                    role = Role.RadioButton,
                                ),
                            ) {
                                RadioButton(
                                    selected = payPayableId == option.id,
                                    onClick = null,
                                )
                                Text(option.name)
                            }
                        }
                        Button(
                            onClick = {
                                val amount = payAmount.toBigDecimalOrNull()
                                val cashId = payCashId
                                val payableId = payPayableId
                                if (amount != null && cashId != null && payableId != null) {
                                    saving = true
                                    coroutineScope.launch {
                                        try {
                                            val payment = Payment(
                                                id = PaymentId(UUID.randomUUID().toString()),
                                                tenantId = TenantId(scope.tenantId),
                                                businessId = BusinessId(scope.businessId),
                                                partyId = PartyId(selected.vendorId),
                                                direction = PaymentDirection.PAID,
                                                methodId = PaymentMethodId("CASH"),
                                                amount = Money.of(
                                                    amount,
                                                    CurrencyCode.of(
                                                        selected.currency.ifBlank { "XXX" },
                                                    ),
                                                ),
                                                relatedInvoiceId = InvoiceId(selected.id),
                                                date = LocalDate.now(),
                                                metadata = EntityMetadata.newLocal(
                                                    Instant.now(),
                                                    DeviceId(scope.deviceId),
                                                ),
                                            )
                                            when (val result = paymentHandler(
                                                payment,
                                                selected.documentNumber,
                                                PurchasePaymentPostingAccounts(cashId, payableId),
                                            )) {
                                                is PurchasePostResult.Posted -> {
                                                    viewModel.applyPayment(scope, selected, amount)
                                                    notice = payReceivedNotice
                                                    error = null
                                                }
                                                is PurchasePostResult.Rejected -> {
                                                    error = payRejectedTemplate.format(
                                                        result.reasons.joinToString(", "),
                                                    )
                                                }
                                            }
                                        } catch (_: Exception) {
                                            error = saveError
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            },
                            enabled = !saving &&
                                (payAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true) &&
                                payCashId != null && payPayableId != null &&
                                selected.status != "PAID" && selected.status != "CANCELLED" &&
                                selected.paidAmount < selected.totalAmount,
                            modifier = Modifier.fillMaxWidth().testTag("payButton"),
                        ) { Text(stringResource(R.string.purchases_pay_button)) }
                    }
                }
            }
            notice?.let { Text(it) }
        }
        if (records.isEmpty()) {
            Text(stringResource(R.string.purchases_empty))
        }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                val displayName = runCatching {
                    JSONObject(record.json).optString("description", record.id)
                }.getOrDefault(record.id)
                Button(onClick = { editingId = record.id; description = displayName }, enabled = !saving) {
                    Text(stringResource(R.string.purchases_edit, displayName))
                }
            }
        }
    }
}
