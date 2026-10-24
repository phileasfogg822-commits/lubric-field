package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import java.time.LocalDate

/**
 * One invoice open for collection. `alreadyPaid` is derived
 * from prior payment rows (21.1: no stored paidAmount on the
 * invoice), never stored on the invoice itself.
 * `collectible` is false for DRAFT/CANCELLED invoices — the
 * caller decides from status, this core only trusts the flag.
 */
data class OpenInvoice(
    val invoiceId: InvoiceId,
    val partyId: PartyId,
    val grandTotal: Money,
    val alreadyPaid: Money,
    val dueDate: LocalDate,
    val collectible: Boolean = true,
)

data class InvoiceApplication(
    val invoiceId: InvoiceId,
    val applied: Money,
    val remaining: Money,
)

data class AllocationResult(
    val applications: List<InvoiceApplication>,
    val unallocated: Money,
)

/**
 * Pure payment allocation. Status-agnostic by design:
 * domain/payment depends only on :domain:common, so this
 * core computes applied amounts and remaining balances and
 * the caller maps remaining == 0 to PAID and applied > 0 to
 * PARTIALLY_PAID.
 *
 * Rules: only RECEIVED payments allocate (PAID direction is
 * the payables side, Phase 23); only same-party invoices;
 * deterministic order by dueDate then invoiceId (oldest
 * first); outstanding = grandTotal minus alreadyPaid clamped
 * at zero; applied = min(remaining payment, outstanding);
 * a currency mismatch skips the invoice instead of forcing
 * a conversion; the leftover is reported as unallocated —
 * the caller decides whether it becomes party credit.
 */
object PaymentAllocation {

    fun allocate(
        payment: Payment,
        openInvoices: List<OpenInvoice>,
    ): AllocationResult {
        if (payment.direction != PaymentDirection.RECEIVED ||
            !payment.amount.isPositive
        ) {
            return AllocationResult(emptyList(), payment.amount)
        }
        var remaining = payment.amount
        val applications = mutableListOf<InvoiceApplication>()
        val ordered = openInvoices.sortedWith(
            compareBy({ it.dueDate }, { it.invoiceId.value }),
        )
        for (invoice in ordered) {
            if (remaining.isZero) break
            if (!invoice.collectible) continue
            if (invoice.partyId != payment.partyId) continue
            if (invoice.grandTotal.currency != payment.amount.currency ||
                invoice.alreadyPaid.currency != payment.amount.currency
            ) {
                continue
            }
            val outstanding = invoice.grandTotal - invoice.alreadyPaid
            if (!outstanding.isPositive) continue
            val applied =
                if (remaining < outstanding) remaining else outstanding
            applications.add(
                InvoiceApplication(
                    invoiceId = invoice.invoiceId,
                    applied = applied,
                    remaining = outstanding - applied,
                ),
            )
            remaining -= applied
        }
        return AllocationResult(applications, remaining)
    }
}
