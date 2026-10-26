package com.lubrcapp.lubrcapp.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.feature.printing.PrintPreviewScreen
import com.lubrcapp.lubrcapp.feature.printing.PrintableDocument
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
 * Download writes the same bytes to the Downloads
 * collection (25.8) and reports success or failure with a
 * Toast. Print hands the same bytes to the system print
 * service (25.9); the system UI owns the job from there.
 */
@Composable
fun PrintingPreviewContent(
    scope: OperationalScope,
    onBack: () -> Unit,
) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    // Pre-Android-10 downloads need the storage permission, which
    // the user grants from the Download tap itself: the tap stashes
    // its action here and the launcher runs it once granted.
    // Declared unconditionally so the launcher survives the
    // empty-state/preview branch switch.
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingDownload?.invoke()
        pendingDownload = null
    }
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
        val shareTitle = stringResource(R.string.feature_printing_share_title)
        val downloadedText = stringResource(R.string.feature_printing_downloaded)
        val failedText = stringResource(R.string.feature_printing_download_failed)
        PrintPreviewScreen(
            document = document,
            onBack = onBack,
            onShare = {
                val bytes = renderPdfBytes(document)
                val fileName = "invoice-${latest.number.ifBlank { latest.id }}.pdf"
                val uri = writeSharedPdf(context, fileName, bytes)
                context.startActivity(
                    Intent.createChooser(
                        pdfShareIntent(uri),
                        shareTitle,
                    ),
                )
            },
            onDownload = {
                val performDownload = {
                    val fileName = "invoice-${latest.number.ifBlank { latest.id }}.pdf"
                    val uri = downloadPdf(context, fileName, renderPdfBytes(document))
                    Toast.makeText(
                        context,
                        if (uri != null) downloadedText else failedText,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingDownload = performDownload
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    performDownload()
                }
            },
            onPrint = {
                printPdf(
                    context,
                    "Invoice ${latest.number.ifBlank { latest.id }}",
                    renderPdfBytes(document),
                )
            },
        )
    }
}

private fun renderPdfBytes(document: PrintableDocument): ByteArray =
    ByteArrayOutputStream().also { out ->
        writePdf(layoutDocument(document), 595, 842, out)
    }.toByteArray()
