package com.lubrcapp.lubrcapp.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.sales.FieldSalesSummaryMapper
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import com.lubrcapp.lubrcapp.feature.sales.SalespersonDashboardScreen
import java.time.LocalDate

/**
 * Phase 27.7 — the field sales module's shell content:
 * the salesperson dashboard over a summary derived
 * from stored sales documents (see
 * [FieldSalesSummaryMapper]), with the shell owning
 * the cross-feature wiring the screen must not see.
 *
 * Take Order opens the POS counter: it is the only
 * reusable order-entry surface in the tree, and it
 * shares the dashboard's gates (MODULE_ORDERS +
 * SALES_VIEW), so no visibility rule is bypassed.
 * It is interim — a fast-order screen (27.2's mapper
 * over a salesperson flow) replaces it when it lands.
 * The other five §108 actions stay absent (their
 * callbacks are null, so no button renders): their
 * destinations need cross-module navigation, and the
 * module content signature carries no navigate
 * parameter — adding one touches every module, so it
 * is a shell-level follow-up, not a dead button here.
 *
 * Collections and target rows are absent until their
 * records persist (see [FieldSalesSummaryMapper]);
 * sales and orders are real from the first frame
 * after lines load (the first composition shows zero
 * sales until the line load completes, then
 * recomposes with the derived total).
 */
@Composable
fun FieldSalesContent(scope: OperationalScope, onBack: () -> Unit) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    val documents by salesViewModel.observeDocuments(scope).collectAsState(initial = emptyList())
    var linesById by remember { mutableStateOf(emptyMap<String, List<SalesDocumentLine>>()) }
    val today = LocalDate.now().toString()
    LaunchedEffect(documents) {
        val wanted = documents.filter {
            it.date == today && it.status != "DRAFT" && it.type == SalesDocumentType.INVOICE
        }
        linesById = wanted.associate { doc -> doc.id to salesViewModel.loadLines(scope, doc.id) }
    }
    var showCounter by remember { mutableStateOf(false) }
    if (showCounter) {
        PosCounterContent(scope) { showCounter = false }
    } else {
        SalespersonDashboardScreen(
            summary = FieldSalesSummaryMapper.summarize(
                documents,
                linesById,
                today,
                CurrencyCode("XXX"),
            ),
            onBack = onBack,
            onTakeOrder = { showCounter = true },
        )
    }
}
