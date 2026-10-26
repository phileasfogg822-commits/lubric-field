package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Phase 27.7 — the salesperson dashboard screen
 * (§108 Salesperson Mode, §158 dashboard). Stateless
 * over the summary: rows render only for non-null
 * values, and action buttons render only for non-null
 * callbacks (the 25.6 nullable-callback pattern) —
 * so the screen never shows a fake zero and never a
 * dead button.
 *
 * §108 visibility, by construction: the summary
 * carries only sales, orders, collections and target
 * — no profit, purchase cost, bank balance, P&L, or
 * other salespeople's data exists anywhere in this
 * screen's inputs.
 *
 * The six §108 actions (My Customers, My Route, Take
 * Order, Collections, My Sales, Targets) are callbacks
 * the shell wires where a gate-safe destination
 * exists; the rest stay absent until cross-module
 * navigation lands (the module content signature
 * carries no navigate parameter).
 */
@Composable
fun SalespersonDashboardScreen(
    summary: FieldSalesSummary,
    onBack: () -> Unit,
    onMyCustomers: (() -> Unit)? = null,
    onMyRoute: (() -> Unit)? = null,
    onTakeOrder: (() -> Unit)? = null,
    onCollections: (() -> Unit)? = null,
    onMySales: (() -> Unit)? = null,
    onTargets: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.fs_back)) }
            Text(stringResource(R.string.fs_title))
        }
        summary.todaySales?.let {
            Text(stringResource(R.string.fs_today_sales, it.amount.toPlainString()))
        }
        summary.orders?.let {
            Text(stringResource(R.string.fs_orders, it.toString()))
        }
        summary.collections?.let {
            Text(stringResource(R.string.fs_collections, it.amount.toPlainString()))
        }
        summary.targetPercent?.let {
            Text(stringResource(R.string.fs_target, it.points.toPlainString()))
        }
        onMyCustomers?.let { action ->
            TextButton(onClick = action) { Text(stringResource(R.string.fs_my_customers)) }
        }
        onMyRoute?.let { action ->
            TextButton(onClick = action) { Text(stringResource(R.string.fs_my_route)) }
        }
        onTakeOrder?.let { action ->
            TextButton(onClick = action) { Text(stringResource(R.string.fs_take_order)) }
        }
        onCollections?.let { action ->
            TextButton(onClick = action) { Text(stringResource(R.string.fs_action_collections)) }
        }
        onMySales?.let { action ->
            TextButton(onClick = action) { Text(stringResource(R.string.fs_my_sales)) }
        }
        onTargets?.let { action ->
            TextButton(onClick = action) { Text(stringResource(R.string.fs_targets)) }
        }
    }
}
