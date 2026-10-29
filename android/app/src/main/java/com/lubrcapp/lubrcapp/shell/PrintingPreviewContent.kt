package com.lubrcapp.lubrcapp.shell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerProfile
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardViewModel
import com.lubrcapp.lubrcapp.feature.parties.PartiesViewModel
import com.lubrcapp.lubrcapp.feature.parties.PartyAvatar
import com.lubrcapp.lubrcapp.feature.printing.PRINT_THEMES
import com.lubrcapp.lubrcapp.feature.printing.PrintTheme
import com.lubrcapp.lubrcapp.feature.printing.ThemedPrintPreviewScreen
import com.lubrcapp.lubrcapp.feature.printing.ThemedRow
import com.lubrcapp.lubrcapp.feature.printing.layoutThemedInvoice
import com.lubrcapp.lubrcapp.feature.printing.layoutThemedStatement
import com.lubrcapp.lubrcapp.feature.printing.writeThemedPdf
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 32.5 — the printing module's shell content: a themed
 * invoice preview or a per-customer statement, chosen by the type
 * selector above the preview. The invoice path reuses the 25.7
 * mapper; the statement path builds the ledger profile for the
 * picked party (receivable rows only, signed amounts, running
 * balances from the profile — never re-derived here). The company
 * name comes from the activity-scoped dashboard state, the party
 * photo from the picked party's photoUri (any load failure falls
 * back to initials). Share, Download and Print all render the
 * same themed PDF bytes the preview shows.
 */
private enum class PrintDocType {
    INVOICE,
    STATEMENT,
}

@Composable
fun PrintingPreviewContent(
    scope: OperationalScope,
    onBack: () -> Unit,
) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val partiesViewModel: PartiesViewModel = hiltViewModel()
    val accountsViewModel: AccountsViewModel = hiltViewModel()
    val context = LocalContext.current
    // Pre-Android-10 downloads need the storage permission, which
    // the user grants from the Download tap itself: the tap stashes
    // its action here and the launcher runs it once granted.
    // Declared unconditionally so the launcher survives branch switches.
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingDownload?.invoke()
        pendingDownload = null
    }
    val dashboardState by dashboardViewModel.state.collectAsState()
    val companyName = dashboardState.currentBusiness?.name.orEmpty()
    var docType by remember { mutableStateOf(PrintDocType.INVOICE) }
    var theme by remember { mutableStateOf(PRINT_THEMES.first()) }

    val invoices by remember(salesViewModel, scope) { salesViewModel.observeInvoices(scope) }
        .collectAsState(initial = emptyList())
    val salesParties by remember(salesViewModel, scope) { salesViewModel.observeParties(scope) }
        .collectAsState(initial = emptyList())
    val latest = latestInvoice(invoices)
    val lines by remember(salesViewModel, scope, latest?.id) {
        salesViewModel.observeLines(scope, latest?.id ?: "")
    }.collectAsState(initial = emptyList())

    fun downloadWithPermission(
        fileName: String,
        bytes: ByteArray,
        downloadedText: String,
        failedText: String,
    ) {
        val performDownload = {
            val uri = downloadPdf(context, fileName, bytes)
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
    }

    fun sharePdf(fileName: String, bytes: ByteArray, chooserTitle: String) {
        val uri = writeSharedPdf(context, fileName, bytes)
        context.startActivity(Intent.createChooser(pdfShareIntent(uri), chooserTitle))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (docType == PrintDocType.INVOICE) {
                Button(onClick = {}) { Text(stringResource(R.string.feature_printing_type_invoice)) }
            } else {
                OutlinedButton(onClick = { docType = PrintDocType.INVOICE }) {
                    Text(stringResource(R.string.feature_printing_type_invoice))
                }
            }
            if (docType == PrintDocType.STATEMENT) {
                Button(onClick = {}) { Text(stringResource(R.string.feature_printing_type_statement)) }
            } else {
                OutlinedButton(onClick = { docType = PrintDocType.STATEMENT }) {
                    Text(stringResource(R.string.feature_printing_type_statement))
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (docType) {
                PrintDocType.INVOICE -> {
                    if (latest == null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(onClick = onBack) { Text(stringResource(R.string.feature_printing_back)) }
                            Text(stringResource(R.string.feature_printing_empty))
                        }
                    } else {
                        val document = remember(latest, lines, salesParties) {
                            invoiceToPrintable(
                                latest,
                                lines,
                                salesParties.firstOrNull { it.id == latest.partyId }?.name,
                            )
                        }
                        val rows = remember(document) { layoutThemedInvoice(document) }
                        val shareTitle = stringResource(R.string.feature_printing_share_title)
                        val downloadedText = stringResource(R.string.feature_printing_downloaded)
                        val failedText = stringResource(R.string.feature_printing_download_failed)
                        ThemedPrintPreviewScreen(
                            headerName = companyName,
                            headerDetail = null,
                            photo = null,
                            title = document.title,
                            theme = theme,
                            onThemeChange = { theme = it },
                            rows = rows,
                            onBack = onBack,
                            onShare = {
                                sharePdf(
                                    "invoice-${latest.number.ifBlank { latest.id }}.pdf",
                                    renderThemedPdfBytes(
                                        document.title, companyName, null, null,
                                        theme.argb.toInt(), rows,
                                    ),
                                    shareTitle,
                                )
                            },
                            onDownload = {
                                downloadWithPermission(
                                    "invoice-${latest.number.ifBlank { latest.id }}.pdf",
                                    renderThemedPdfBytes(
                                        document.title, companyName, null, null,
                                        theme.argb.toInt(), rows,
                                    ),
                                    downloadedText,
                                    failedText,
                                )
                            },
                            onPrint = {
                                printPdf(
                                    context,
                                    "Invoice ${latest.number.ifBlank { latest.id }}",
                                    renderThemedPdfBytes(
                                        document.title, companyName, null, null,
                                        theme.argb.toInt(), rows,
                                    ),
                                )
                            },
                        )
                    }
                }
                PrintDocType.STATEMENT -> {
                    StatementPreview(
                        scope = scope,
                        companyName = companyName,
                        theme = theme,
                        onThemeChange = { theme = it },
                        onBack = onBack,
                        partiesViewModel = partiesViewModel,
                        accountsViewModel = accountsViewModel,
                        downloadWithPermission = ::downloadWithPermission,
                        sharePdf = ::sharePdf,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatementPreview(
    scope: OperationalScope,
    companyName: String,
    theme: PrintTheme,
    onThemeChange: (PrintTheme) -> Unit,
    onBack: () -> Unit,
    partiesViewModel: PartiesViewModel,
    accountsViewModel: AccountsViewModel,
    downloadWithPermission: (String, ByteArray, String, String) -> Unit,
    sharePdf: (String, ByteArray, String) -> Unit,
) {
    val context = LocalContext.current
    val parties by remember(partiesViewModel, scope) { partiesViewModel.observeParties(scope) }
        .collectAsState(initial = emptyList())
    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    val selectedParty = parties.firstOrNull { it.id == selectedPartyId }
    if (selectedParty == null) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.feature_printing_back)) }
            Text(stringResource(R.string.feature_printing_pick_party))
            if (parties.isEmpty()) {
                Text(stringResource(R.string.feature_printing_no_parties))
            }
            parties.forEach { party ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clickable { selectedPartyId = party.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PartyAvatar(photoUri = party.photoUri, name = party.name)
                    Text(text = party.name, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        return
    }
    var profile by remember { mutableStateOf<PartyLedgerProfile.Profile?>(null) }
    LaunchedEffect(accountsViewModel, scope, selectedParty.id) {
        profile = null
        val rows = accountsViewModel.loadLedgerRowsForParty(scope, selectedParty.id)
        val currency = rows.firstOrNull()?.currency?.takeIf { it.isNotBlank() } ?: "XXX"
        profile = PartyLedgerProfile.build(rows, selectedParty.id, CurrencyCode(currency))
    }
    val photoBitmap by produceState<Bitmap?>(initialValue = null, selectedParty.photoUri) {
        value = loadPartyPhotoBitmap(context, selectedParty.photoUri)
    }
    val loaded = profile
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.feature_printing_loading))
        }
        return
    }
    val currency = loaded.transactions.firstOrNull()?.currency?.takeIf { it.isNotBlank() } ?: "XXX"
    val rows = remember(loaded) {
        layoutThemedStatement(
            selectedParty.name,
            currency,
            profileToStatementInputs(loaded),
            loaded.balance.amount,
        )
    }
    val shareTitle = stringResource(R.string.feature_printing_share_statement)
    val downloadedText = stringResource(R.string.feature_printing_statement_downloaded)
    val failedText = stringResource(R.string.feature_printing_statement_download_failed)
    ThemedPrintPreviewScreen(
        headerName = selectedParty.name,
        headerDetail = companyName.takeIf { it.isNotBlank() },
        photo = photoBitmap?.asImageBitmap(),
        title = stringResource(R.string.feature_printing_type_statement),
        theme = theme,
        onThemeChange = onThemeChange,
        rows = rows,
        onBack = { selectedPartyId = null },
        onShare = {
            sharePdf(
                "statement-${selectedParty.id}.pdf",
                renderThemedPdfBytes(
                    "Statement", selectedParty.name, companyName.takeIf { it.isNotBlank() },
                    photoBitmap, theme.argb.toInt(), rows,
                ),
                shareTitle,
            )
        },
        onDownload = {
            downloadWithPermission(
                "statement-${selectedParty.id}.pdf",
                renderThemedPdfBytes(
                    "Statement", selectedParty.name, companyName.takeIf { it.isNotBlank() },
                    photoBitmap, theme.argb.toInt(), rows,
                ),
                downloadedText,
                failedText,
            )
        },
        onPrint = {
            printPdf(
                context,
                "Statement ${selectedParty.name}",
                renderThemedPdfBytes(
                    "Statement", selectedParty.name, companyName.takeIf { it.isNotBlank() },
                    photoBitmap, theme.argb.toInt(), rows,
                ),
            )
        },
    )
}

private fun renderThemedPdfBytes(
    title: String,
    headerName: String,
    headerDetail: String?,
    photo: Bitmap?,
    themeArgb: Int,
    rows: List<ThemedRow>,
): ByteArray =
    ByteArrayOutputStream().also { out ->
        writeThemedPdf(title, headerName, headerDetail, photo, themeArgb, rows, 595, 842, out)
    }.toByteArray()

/**
 * Party photo for the statement header: bounds pass plus sampling
 * so a camera-size photo is never decoded at full size for a badge.
 * Any failure (blank URI, bad image, no permission, no network)
 * returns null and the header falls back to initials.
 */
private suspend fun loadPartyPhotoBitmap(context: Context, uri: String?): Bitmap? =
    withContext(Dispatchers.IO) {
        if (uri.isNullOrBlank()) return@withContext null
        runCatching {
            fun open() = if (uri.startsWith("http")) {
                URL(uri).openStream()
            } else {
                context.contentResolver.openInputStream(Uri.parse(uri))
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 192)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            open()?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }
