package com.lubrcapp.lubrcapp.domain.reporting

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ReportRequest(
    val type: ReportType,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val filters: Map<String, String> = emptyMap(),
)

data class ReportResult(
    val type: ReportType,
    val generatedAt: Instant,
    val rows: List<ReportRow> = emptyList(),
    val summary: Map<String, ReportValue> = emptyMap(),
)

data class ReportRow(
    val labels: List<String> = emptyList(),
    val values: List<ReportValue> = emptyList(),
)

sealed interface ReportValue {
    data class MoneyValue(val value: Money) : ReportValue
    data class QuantityValue(val value: Quantity) : ReportValue
    data class DecimalValue(val value: BigDecimal) : ReportValue
    data class IntegerValue(val value: Long) : ReportValue
    data class TextValue(val value: String) : ReportValue
}

enum class ReportType {
    SALES_SUMMARY,
    PURCHASE_SUMMARY,
    PROFIT_LOSS,
    BALANCE_SHEET,
    TAX_SUMMARY,
    INVENTORY_VALUATION,
    PARTY_STATEMENT,
    CASH_FLOW,
    TOP_ITEMS,
    TOP_PARTIES,
}
