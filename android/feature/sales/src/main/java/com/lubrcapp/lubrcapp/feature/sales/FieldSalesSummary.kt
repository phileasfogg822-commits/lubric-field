package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage

/**
 * Phase 27.7 — the salesperson dashboard summary
 * (§108 Salesperson Mode, §158 Salesperson
 * Dashboard). Display data only: Today's Sales,
 * Orders, Collections, Target %.
 *
 * Collections and target are NULLABLE by design, not
 * zero: neither collections (27.3) nor targets (27.6)
 * persist yet, so there is nothing real to sum — the
 * screen omits null rows rather than showing a fake
 * zero. They become real when their records persist.
 * Today's sales and orders ARE derived, from the
 * sales documents that already persist.
 */
data class FieldSalesSummary(
    val todaySales: Money?,
    val orders: Int?,
    val collections: Money? = null,
    val targetPercent: Percentage? = null,
)

/**
 * Derives the dashboard summary from stored sales
 * documents. Rules, all explicit:
 * - Only documents dated [today] with status other
 *   than DRAFT count — drafts are uncommitted work,
 *   and undated documents (date "") never match today.
 * - Today's sales = the totals-engine grand total of
 *   today's non-draft INVOICE documents, summed in
 *   [currency] (the document's currency is the
 *   business currency, supplied by the caller — it is
 *   not stored on the document, per 18.1).
 * - Orders = the count of today's non-draft
 *   SALES_ORDER documents.
 * - Collections and target stay null: see
 *   [FieldSalesSummary].
 */
object FieldSalesSummaryMapper {
    fun summarize(
        documents: List<SalesDocument>,
        linesByOrderId: Map<String, List<SalesDocumentLine>>,
        today: String,
        currency: CurrencyCode,
    ): FieldSalesSummary {
        val todays = documents.filter { it.date == today && it.status != "DRAFT" }
        val sales = todays
            .filter { it.type == SalesDocumentType.INVOICE }
            .fold(Money.zero(currency)) { acc, doc ->
                acc + SalesDocumentEngine.totals(linesByOrderId[doc.id].orEmpty(), currency).grandTotal
            }
        val orders = todays.count { it.type == SalesDocumentType.SALES_ORDER }
        return FieldSalesSummary(todaySales = sales, orders = orders)
    }
}
