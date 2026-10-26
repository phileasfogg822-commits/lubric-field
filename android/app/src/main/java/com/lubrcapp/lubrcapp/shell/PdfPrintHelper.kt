package com.lubrcapp.lubrcapp.shell

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PageRange
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
 * service. The adapter is thin glue over two testable
 * functions (printDocumentInfo, copyBytesToFileDescriptor);
 * the instrumented tests exercise those directly, since
 * the framework callback classes cannot be subclassed
 * outside android.print. The print() call itself opens
 * the system UI and is intentionally not asserted — the
 * same thin-glue class as the share/download content
 * wiring.
 */
/**
 * Named subclass (the official printing sample's shape).
 * Note the import above: PageRange is a TOP-LEVEL
 * android.print class, not a nested type of
 * PrintDocumentAdapter — the earlier heads failed because
 * they referenced it as nested (bare, qualified, and
 * imported-nested all fail; only the top-level import
 * resolves). The compiler's own expected-signature
 * rendering shows it unqualified because the base class
 * shares its package.
 *
 * Testability: the framework callback classes have
 * package-private constructors, so app code cannot
 * subclass them in tests — the adapter itself is thin
 * glue over two testable functions (printDocumentInfo
 * and copyBytesToFileDescriptor), which the instrumented
 * tests exercise directly without any callback subclass.
 */
fun printDocumentInfo(jobName: String): PrintDocumentInfo =
    PrintDocumentInfo.Builder(jobName)
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
        .build()

@Throws(IOException::class)
fun copyBytesToFileDescriptor(bytes: ByteArray, destination: ParcelFileDescriptor) {
    FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
}

private class PdfPrintDocumentAdapter(
    private val jobName: String,
    private val bytes: ByteArray,
) : PrintDocumentAdapter() {
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
        val info = printDocumentInfo(jobName)
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
            copyBytesToFileDescriptor(bytes, destination)
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback?.onWriteFailed(e.message)
        }
    }
}

fun pdfPrintAdapter(jobName: String, bytes: ByteArray): PrintDocumentAdapter =
    PdfPrintDocumentAdapter(jobName, bytes)

fun printPdf(context: Context, jobName: String, bytes: ByteArray): PrintJob? {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        ?: return null
    return printManager.print(jobName, pdfPrintAdapter(jobName, bytes), null)
}
