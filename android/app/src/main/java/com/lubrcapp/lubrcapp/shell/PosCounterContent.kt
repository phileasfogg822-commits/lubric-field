package com.lubrcapp.lubrcapp.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.sales.PosCart
import com.lubrcapp.lubrcapp.domain.sales.PosCartLine
import com.lubrcapp.lubrcapp.domain.sales.PosCartOps
import com.lubrcapp.lubrcapp.feature.printing.PrintPreviewScreen
import com.lubrcapp.lubrcapp.feature.printing.PrintableDocument
import com.lubrcapp.lubrcapp.feature.printing.layoutDocument
import com.lubrcapp.lubrcapp.feature.printing.writePdf
import com.lubrcapp.lubrcapp.feature.sales.ItemOption
import com.lubrcapp.lubrcapp.feature.sales.PosCounterScreen
import com.lubrcapp.lubrcapp.feature.sales.PosHoldMapper
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Phase 26.7 — the POS module's shell content: the counter
 * screen over a remembered cart, with the shell owning the
 * cross-feature wiring the screen must not see (feature
 * modules never depend on each other).
 *
 * Quantity edits go through 26.1's cart ops (decrease at
 * quantity 1 is a no-op — removal has its own button, and
 * the ops reject zero quantities). Search filters the
 * sales item flow to priced name matches; tapping a result
 * adds one unit at its sale rate, merging with an identical
 * row per 26.1. Hold saves the cart as a DRAFT invoice
 * through 26.2's mapper and clears the counter. Pay renders
 * the cart through the 25.x print chain (share / download /
 * system print, wired exactly as the invoice preview is);
 * payment posting itself rides 22.x when the held invoice is
 * finalized from the sales screen — the counter does not
 * post. Scan stays unbuilt: the Phase 16 camera decision
 * is still the owner's call.
 */
@Composable
fun PosCounterContent(
    scope: OperationalScope,
    onBack: () -> Unit,
) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    val coroutineScope = rememberCoroutineScope()
    var cart by remember { mutableStateOf(PosCart(id = UUID.randomUUID().toString())) }
    var searchResults by remember { mutableStateOf<List<ItemOption>>(emptyList()) }
    var payPreview by remember { mutableStateOf<PrintableDocument?>(null) }
    val items by remember(salesViewModel, scope) { salesViewModel.observeItems(scope) }
        .collectAsState(initial = emptyList())
    // Declared unconditionally so the launcher survives the
    // counter/preview branch switch, as in the invoice preview.
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingDownload?.invoke()
        pendingDownload = null
    }
    val document = payPreview
    if (document != null) {
        val context = LocalContext.current
        val shareTitle = stringResource(R.string.feature_printing_share_title)
        val downloadedText = stringResource(R.string.feature_printing_downloaded)
        val failedText = stringResource(R.string.feature_printing_download_failed)
        PrintPreviewScreen(
            document = document,
            onBack = { payPreview = null },
            onShare = {
                val bytes = renderPosPdfBytes(document)
                val fileName = "pos-${cart.id}.pdf"
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
                    val fileName = "pos-${cart.id}.pdf"
                    val uri = downloadPdf(context, fileName, renderPosPdfBytes(document))
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
                    "POS ${cart.id}",
                    renderPosPdfBytes(document),
                )
            },
        )
    } else {
        PosCounterScreen(
            cart = cart,
            onBack = onBack,
            onIncreaseQuantity = { lineId ->
                cart.lines.firstOrNull { it.id == lineId }?.let { line ->
                    cart = PosCartOps.updateQuantity(cart, lineId, line.quantity + Quantity.of("1"))
                }
            },
            onDecreaseQuantity = { lineId ->
                cart.lines.firstOrNull { it.id == lineId }?.let { line ->
                    if (line.quantity.amount > BigDecimal.ONE) {
                        cart = PosCartOps.updateQuantity(
                            cart,
                            lineId,
                            line.quantity - Quantity.of("1"),
                        )
                    }
                }
            },
            onRemoveLine = { lineId ->
                cart = PosCartOps.removeLine(cart, lineId)
            },
            onHold = {
                val held = cart
                coroutineScope.launch {
                    salesViewModel.saveDocumentWithLines(
                        scope,
                        PosHoldMapper.cartToDocument(held),
                        PosHoldMapper.cartToLines(held),
                    )
                    cart = PosCart(id = UUID.randomUUID().toString())
                    searchResults = emptyList()
                }
            },
            onPay = {
                payPreview = cartToPrintable(cart, cartPartyName(cart))
            },
            onSearchSubmit = { query ->
                searchResults = if (query.isBlank()) {
                    emptyList()
                } else {
                    items.filter { option ->
                        option.saleRate != null &&
                            option.name.contains(query, ignoreCase = true)
                    }
                }
            },
            searchResults = searchResults,
            onAddSearchResult = { option ->
                val rate = option.saleRate
                if (rate != null) {
                    cart = PosCartOps.addLine(
                        cart,
                        PosCartLine(
                            id = UUID.randomUUID().toString(),
                            itemId = ItemId(option.id),
                            description = option.name,
                            quantity = Quantity.of("1"),
                            rate = Money.of(rate, CurrencyCode("XXX")),
                        ),
                    )
                }
            },
        )
    }
}

private fun renderPosPdfBytes(document: PrintableDocument): ByteArray =
    ByteArrayOutputStream().also { out ->
        writePdf(layoutDocument(document), 595, 842, out)
    }.toByteArray()
