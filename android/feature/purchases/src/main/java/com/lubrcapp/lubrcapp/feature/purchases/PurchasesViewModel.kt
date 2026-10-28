package com.lubrcapp.lubrcapp.feature.purchases

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first purchases feature: observes Room and queues writes without network access. */
@HiltViewModel
class PurchasesViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "purchase_orders")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "purchase_orders")

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "purchase_orders", id, payloadJson, revision)

    /** Phase 23.1 — typed payables surface over "vendor_payables". */
    fun observePayables(scope: OperationalScope): Flow<List<VendorPayableRecord>> =
        repository.observe(scope, "vendor_payables")
            .map { records -> records.map { VendorPayableJson.decode(it.id, it.json) } }

    suspend fun savePayable(scope: OperationalScope, record: VendorPayableRecord) =
        repository.save(scope, "vendor_payables", record.id, VendorPayableJson.encode(record))

    // Phase 31.2 — apply a posted supplier payment to a payable.
    // The journal posting happens in the shell BEFORE this runs;
    // this only moves the paid amount and the status. Overpayment
    // clamps at the total; an already fully-paid payable is left
    // untouched (no duplicate save).
    suspend fun applyPayment(
        scope: OperationalScope,
        payable: VendorPayableRecord,
        amount: java.math.BigDecimal,
    ): VendorPayableRecord {
        if (payable.status == "PAID" || payable.status == "CANCELLED" ||
            payable.paidAmount >= payable.totalAmount
        ) {
            return payable
        }
        val newPaid = (payable.paidAmount + amount).min(payable.totalAmount)
        val updated = payable.copy(
            paidAmount = newPaid,
            status = if (newPaid >= payable.totalAmount) "PAID" else "PARTIALLY_PAID",
        )
        savePayable(scope, updated)
        return updated
    }

    suspend fun loadPayables(scope: OperationalScope): List<VendorPayableRecord> =
        repository.list(scope, "vendor_payables")
            .map { VendorPayableJson.decode(it.id, it.json) }

    /** One payable per purchase: the record id IS the purchase document id. */
    suspend fun loadPayableForPurchase(scope: OperationalScope, purchaseId: String): VendorPayableRecord? =
        loadPayables(scope).firstOrNull { it.id == purchaseId }
}
