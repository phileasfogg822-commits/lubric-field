package com.lubrcapp.lubrcapp.domain.purchase

import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.domain.payment.PaymentDirection

/**
 * Phase 23.4 (STAGED — integrates after 23.3 accepts) —
 * vendor payment application: the derivation of a
 * purchase invoice's paidAmount (supervisor decision
 * 1). The stored paidAmount is a CACHE; this
 * function is the authority, recomputed from the
 * payment set on every payment event, mirroring
 * sales (22.2's applyPayment shape, adapted from an
 * event application to a re-derivation).
 *
 * Rules, in order:
 * 1. Only PAID-direction payments linked to this
 *    invoice count: the link
 *    (relatedInvoiceId == invoice id, compared by
 *    value) is the authority. RECEIVED payments and
 *    payments linked to other invoices are ignored.
 * 2. An empty derivation is not an event: zero
 *    counted payments returns the invoice unchanged
 *    (22.2's zero-application rule).
 * 3. Re-derivation is idempotent: when the derived
 *    amount already equals the stored paidAmount,
 *    the invoice returns unchanged whatever its
 *    status — re-running the derivation on a PAID
 *    invoice must not throw.
 * 4. Otherwise the invoice must be RECEIVED,
 *    PARTIALLY_PAID, or OVERDUE; any other status
 *    (DRAFT, PAID with a NEW amount, CANCELLED)
 *    rejects the event, mirroring 22.2's
 *    from-state rule.
 * 5. Target status: derived >= total → PAID, else
 *    PARTIALLY_PAID. An overpayment lands PAID with
 *    paidAmount above total, so balanceDue goes
 *    negative — the honest overpaid signal, not a
 *    clamp.
 *
 * Mixed-currency payment sets fail loudly: Money
 * addition throws on a currency mismatch, which is
 * the engine's currency guard, not a case to paper
 * over.
 *
 * The credit term (debit notes) is NOT in this
 * function yet: decision 1 names "payment/credit
 * events", but no credit type exists until 23.5.
 * When debit notes land, the derivation gains its
 * credit term here; until then this pins
 * payment-only behavior. Syncing the payable
 * record's cached paidAmount (23.1) belongs to the
 * later join package, not to this pure function.
 */
object PurchasePaymentApplication {

    fun applyPayments(
        invoice: PurchaseInvoice,
        payments: List<Payment>,
    ): PurchaseInvoice {
        val counted = payments.filter { payment ->
            payment.direction == PaymentDirection.PAID &&
                payment.relatedInvoiceId?.value == invoice.id.value
        }
        val derived = counted.fold(Money.zero(invoice.total.currency)) { acc, payment ->
            acc + payment.amount
        }
        if (derived.isZero) return invoice
        if (derived.amount.compareTo(invoice.paidAmount.amount) == 0) return invoice
        require(
            invoice.status == PurchaseStatus.RECEIVED ||
                invoice.status == PurchaseStatus.PARTIALLY_PAID ||
                invoice.status == PurchaseStatus.OVERDUE,
        ) {
            "Purchase in ${invoice.status} cannot take payments: ${invoice.id.value}"
        }
        val target = if (derived.amount.compareTo(invoice.total.amount) >= 0) {
            PurchaseStatus.PAID
        } else {
            PurchaseStatus.PARTIALLY_PAID
        }
        return invoice.copy(paidAmount = derived, status = target)
    }
}
