package com.lubrcapp.lubrcapp.shell

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.feature.printing.PrintPreviewScreen
import com.lubrcapp.lubrcapp.feature.printing.layoutDocument
import com.lubrcapp.lubrcapp.feature.printing.writePdf
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.io.ByteArrayOutputStream

/**
 * Phase 25.7 — the printing module's shell content: the latest
 * invoice previewed, or the empty state when no invoice exists.
 * The invoice, party, and line flows all come from the
 * activity-scoped SalesViewModel — the same pattern the
 * parties/sales registry entries use — so the shell, not the
 * printing module, owns the cross-feature read (feature modules
 * never depend on each other). The Share action renders the
 * document to PDF bytes and hands them to the share helper;
 * Download and Print stay hidden until their packages land
 * (their callbacks are null — no dead buttons, the 13.4 rule).
 */
@Composable
fun PrintingPreviewContent(
    scope: OperationalScope,
    onBack: () -> Unit,
) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    val invoices by remember(salesViewModel, scope) { salesViewModel.observeInvoices(scope) }
        .collectAsState(initial = emptyList())
    val parties by remember(salesViewModel, scope) { salesViewModel.observeParties(scope) }
        .collectAsState(initial = emptyList())
    val latest = latestInvoice(invoices)
    val lines by remember(salesViewModel, scope, latest?.id) {
        salesViewModel.observeLines(scope, latest?.id ?: "")
    }.collectAsState(initial = emptyList())
    if (latest == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.feature_printing_back)) }
            Text(stringResource(R.string.feature_printing_empty))
        }
    } else {
        val document = remember(latest, lines, parties) {
            invoiceToPrintable(
                latest,
                lines,
                parties.firstOrNull { it.id == latest.partyId }?.name,
            )
        }
        val context = LocalContext.current
        PrintPreviewScreen(
            document = document,
            onBack = onBack,
            onShare = {
                val bytes = ByteArrayOutputStream().also { out ->
                    writePdf(layoutDocument(document), 595, 842, out)
                }.toByteArray()
                val fileName = "invoice-${latest.number.ifBlank { latest.id }}.pdf"
                val uri = writeSharedPdf(context, fileName, bytes)
                context.startActivity(
                    Intent.createChooser(
                        pdfShareIntent(uri),
                        context.getString(R.string.feature_printing_share_title),
                    ),
                )
            },
            onDownload = null,
            onPrint = null,
        )
    }
}
