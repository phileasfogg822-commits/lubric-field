package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Phase 29.4 — automation evaluators (§104).
 * Pure functions over injected snapshots (the
 * Phase 22 availability-check port pattern):
 * the caller supplies today's data, the
 * evaluators return the notifications that data
 * warrants, and nothing here reads a repository,
 * posts anything, or moves money or stock.
 * Empty snapshots yield empty results, never
 * errors.
 *
 * Rules: low stock fires when quantity is
 * strictly below threshold; payment overdue
 * reuses the §103 strict-past-due rule; stock
 * expiry fires when the expiry date is today or
 * past (no warning window is invented here);
 * purchase due fires when the expected date is
 * reached and the order is unreceived; credit
 * limit warning fires when outstanding is
 * strictly above the limit, same currency
 * required (a mismatch throws).
 */
data class StockLevel(
    val itemId: String,
    val itemName: String,
    val quantity: BigDecimal,
    val threshold: BigDecimal,
)

data class OverdueInvoiceInput(
    val invoiceId: String,
    val partyName: String,
    val dueDate: String,
    val paid: Boolean,
)

data class ExpiringStockInput(
    val itemId: String,
    val itemName: String,
    val expiryDate: String,
)

data class PurchaseDueInput(
    val orderId: String,
    val supplierName: String,
    val expectedDate: String,
    val received: Boolean,
)

data class CreditExposure(
    val partyId: String,
    val partyName: String,
    val outstanding: Money,
    val limit: Money,
)

object AutomationEvaluators {
    fun lowStock(levels: List<StockLevel>, today: String): List<Notification> =
        levels.filter { it.quantity < it.threshold }.map {
            Notification(
                id = "low-${it.itemId}",
                type = NotificationType.LOW_STOCK,
                title = it.itemName,
                detail = "Only ${it.quantity.toPlainString()} left",
                date = today,
            )
        }

    fun overdueInvoices(invoices: List<OverdueInvoiceInput>, today: String): List<Notification> =
        invoices.filter { !it.paid && ReminderSchedule.isOverdue(it.dueDate, today) }.map {
            Notification(
                id = "overdue-${it.invoiceId}",
                type = NotificationType.PAYMENT_OVERDUE,
                title = it.partyName,
                detail = "Invoice ${it.invoiceId} overdue",
                date = today,
            )
        }

    fun expiringStock(items: List<ExpiringStockInput>, today: String): List<Notification> {
        val day = LocalDate.parse(today)
        return items.filter { LocalDate.parse(it.expiryDate) <= day }.map {
            Notification(
                id = "expiry-${it.itemId}",
                type = NotificationType.STOCK_EXPIRY,
                title = it.itemName,
                detail = "Expires ${it.expiryDate}",
                date = today,
            )
        }
    }

    fun purchasesDue(orders: List<PurchaseDueInput>, today: String): List<Notification> {
        val day = LocalDate.parse(today)
        return orders.filter { !it.received && LocalDate.parse(it.expectedDate) <= day }.map {
            Notification(
                id = "due-${it.orderId}",
                type = NotificationType.PURCHASE_DUE,
                title = it.supplierName,
                detail = "Order ${it.orderId} due",
                date = today,
            )
        }
    }

    fun creditWarnings(exposures: List<CreditExposure>, today: String): List<Notification> =
        exposures.map {
            require(it.outstanding.currency == it.limit.currency) {
                "Currency mismatch for ${it.partyId}"
            }
            it
        }.filter { it.outstanding > it.limit }.map {
            Notification(
                id = "credit-${it.partyId}",
                type = NotificationType.CREDIT_LIMIT_WARNING,
                title = it.partyName,
                detail = "Outstanding ${it.outstanding.amount.toPlainString()} over limit",
                date = today,
            )
        }

    fun evaluateAll(
        levels: List<StockLevel>,
        invoices: List<OverdueInvoiceInput>,
        expiring: List<ExpiringStockInput>,
        orders: List<PurchaseDueInput>,
        exposures: List<CreditExposure>,
        today: String,
    ): List<Notification> =
        lowStock(levels, today) +
            overdueInvoices(invoices, today) +
            expiringStock(expiring, today) +
            purchasesDue(orders, today) +
            creditWarnings(exposures, today)
}
