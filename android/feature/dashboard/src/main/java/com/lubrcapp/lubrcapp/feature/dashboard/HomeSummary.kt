package com.lubrcapp.lubrcapp.feature.dashboard

import java.math.BigDecimal

/**
 * Phase 33.2 — Home money overview. Pure data only: the shell
 * computes it from ledger rows (see HomeSummaryMapping) so this
 * module never depends on another feature module. Amounts are
 * signed nets in [currency]; a negative receivable is a credit
 * balance, exactly as the ledger profile reports it.
 */
data class HomeSummary(
    val receivable: BigDecimal,
    val payable: BigDecimal,
    val currency: String,
)

fun homeMoneyText(amount: BigDecimal, currency: String): String =
    amount.toPlainString() + " " + currency
