package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.sales.PaymentPostingAccounts
import com.lubrcapp.lubrcapp.feature.sales.SalesPaymentPosting
import com.lubrcapp.lubrcapp.feature.sales.SalesPostResult
import java.time.Instant

/**
 * Phase 31.2 — the app-layer payment join, mirroring
 * [SalesPostingHandler] but without the inventory half: a payment
 * moves no stock and flips no serials, so the sequence is only
 * build-then-post. The screen owns the amount and the account
 * pickers; this handler owns the posting. A rejected posting
 * changes nothing — the screen calls
 * [com.lubrcapp.lubrcapp.feature.sales.SalesViewModel.applyReceipt]
 * only on [SalesPostResult.Posted].
 *
 * The journal entry carries the document number of the invoice
 * the user was viewing when they received the money; the
 * per-invoice allocation detail lives in the timeline entries
 * applyReceipt writes, because one receipt may spread across
 * several invoices.
 */
object SalesPaymentHandler {

    suspend fun postPayment(
        scope: OperationalScope,
        payment: Payment,
        documentNumber: String,
        accounts: PaymentPostingAccounts,
        accountsViewModel: AccountsViewModel,
    ): SalesPostResult {
        val posting = SalesPaymentPosting.paymentToPosting(payment, documentNumber, accounts)
        val result = accountsViewModel.postDocument(
            scope = scope,
            posting = posting,
            date = payment.date,
            now = Instant.now(),
            deviceId = DeviceId(scope.deviceId),
        )
        if (result is AccountsViewModel.DocumentPostResult.Rejected) {
            return SalesPostResult.Rejected(result.violations.map { it.name })
        }
        return SalesPostResult.Posted(documentNumber, emptyList())
    }
}
