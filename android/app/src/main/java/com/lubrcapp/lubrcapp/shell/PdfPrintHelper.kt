package com.lubrcapp.lubrcapp.shell

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintManager
import java.io.FileOutputStream
import java.io.IOException

/**
 * Phase 25.9 — system Print (§99): hands pre-rendered PDF bytes
 * to Android's print framework. The adapter is the only new
 * logic: onLayout reports the job (page count unknown — the
 * bytes are pre-rendered, never parsed), onWrite copies the
 * bytes to the spooler's file descriptor. printPdf() returns
 * the system PrintJob; the caller ignores it — the system
 * print UI owns the job from there. No permission, no new
 * dependency, no manifest change: printing is a system
 * service. The adapter is exercised directly by an
 * instrumented test (layout + write round-trip through a
 * real file descriptor); the print() call itself opens the
 * system UI and is intentionally not asserted — the same
 * thin-glue class as the share/download content wiring.
 */
fun pdfPrintAdapter(jobName: String, bytes: ByteArray): PrintDocumentAdapter =
    object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<PageRange>,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
                return
            }
            if (destination == null) {
                callback?.onWriteFailed("No destination")
                return
            }
            try {
                FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: IOException) {
                callback?.onWriteFailed(e.message)
            }
        }
    }

fun printPdf(context: Context, jobName: String, bytes: ByteArray): PrintJob? {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        ?: return null
    return printManager.print(jobName, pdfPrintAdapter(jobName, bytes), null)
}
