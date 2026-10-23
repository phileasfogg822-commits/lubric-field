package com.lubrcapp.lubrcapp.domain.sales

import java.math.BigDecimal

/**
 * Document lifecycle rules (master spec §48 quotation statuses,
 * §44 partial fulfilment). The spec asks for "a reusable
 * status/state engine" (§43 area) — this is it: pure transition
 * tables plus the fulfilment derivation, shared by every document
 * type instead of re-implemented per screen. Same-state moves are
 * NOT transitions and are never allowed by these tables.
 */
enum class QuotationStatus {
    DRAFT,
    SENT,
    VIEWED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CONVERTED,
}

enum class SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    CANCELLED,
}

object DocumentStatusRules {

    private val quotationTransitions: Map<QuotationStatus, Set<QuotationStatus>> = mapOf(
        QuotationStatus.DRAFT to setOf(QuotationStatus.SENT, QuotationStatus.EXPIRED),
        QuotationStatus.SENT to setOf(
            QuotationStatus.VIEWED,
            QuotationStatus.ACCEPTED,
            QuotationStatus.REJECTED,
            QuotationStatus.EXPIRED,
        ),
        QuotationStatus.VIEWED to setOf(
            QuotationStatus.ACCEPTED,
            QuotationStatus.REJECTED,
            QuotationStatus.EXPIRED,
        ),
        QuotationStatus.ACCEPTED to setOf(QuotationStatus.CONVERTED),
        QuotationStatus.REJECTED to emptySet(),
        QuotationStatus.EXPIRED to emptySet(),
        QuotationStatus.CONVERTED to emptySet(),
    )

    private val salesOrderTransitions: Map<SalesOrderStatus, Set<SalesOrderStatus>> = mapOf(
        SalesOrderStatus.DRAFT to setOf(SalesOrderStatus.CONFIRMED, SalesOrderStatus.CANCELLED),
        SalesOrderStatus.CONFIRMED to setOf(
            SalesOrderStatus.PARTIALLY_FULFILLED,
            SalesOrderStatus.FULFILLED,
            SalesOrderStatus.CANCELLED,
        ),
        SalesOrderStatus.PARTIALLY_FULFILLED to setOf(
            SalesOrderStatus.FULFILLED,
            SalesOrderStatus.CANCELLED,
        ),
        SalesOrderStatus.FULFILLED to emptySet(),
        SalesOrderStatus.CANCELLED to emptySet(),
    )

    fun canTransition(from: QuotationStatus, to: QuotationStatus): Boolean =
        quotationTransitions[from].orEmpty().contains(to)

    fun canTransition(from: SalesOrderStatus, to: SalesOrderStatus): Boolean =
        salesOrderTransitions[from].orEmpty().contains(to)

    fun isTerminal(status: QuotationStatus): Boolean = quotationTransitions[status].isNullOrEmpty()

    fun isTerminal(status: SalesOrderStatus): Boolean = salesOrderTransitions[status].isNullOrEmpty()

    /**
     * Derive an order's fulfilment status from its lines'
     * (ordered, fulfilled) quantities (§44). Returns null when
     * nothing has been fulfilled yet — the caller keeps whatever
     * pre-fulfilment status the order has (DRAFT / CONFIRMED).
     * A line counts complete when fulfilled >= ordered (an
     * over-delivery completes the line; it is not an error at
     * this layer). An order with no lines derives nothing.
     */
    fun fulfilmentStatus(lines: List<Pair<BigDecimal, BigDecimal>>): SalesOrderStatus? {
        if (lines.isEmpty()) return null
        val anyFulfilled = lines.any { (_, fulfilled) -> fulfilled.signum() > 0 }
        if (!anyFulfilled) return null
        val allComplete = lines.all { (ordered, fulfilled) -> fulfilled >= ordered }
        return if (allComplete) SalesOrderStatus.FULFILLED else SalesOrderStatus.PARTIALLY_FULFILLED
    }
}
