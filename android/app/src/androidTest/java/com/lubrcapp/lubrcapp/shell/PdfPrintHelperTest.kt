package com.lubrcapp.lubrcapp.shell

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPrintHelperTest {

    @Test
    fun layoutReportsDocumentJob() {
        var finishedInfo: PrintDocumentInfo? = null
        var layoutChanged = false
        val callback = object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                finishedInfo = info
                layoutChanged = changed
            }
        }
        pdfPrintAdapter("Invoice INV-1", byteArrayOf(1, 2, 3)).onLayout(
            null,
            null,
            CancellationSignal(),
            callback,
            null,
        )
        assertNotNull(finishedInfo)
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, finishedInfo!!.contentType)
        assertEquals(PrintDocumentInfo.PAGE_COUNT_UNKNOWN, finishedInfo!!.pageCount)
        assertTrue(layoutChanged)
    }

    @Test
    fun writeCopiesBytesToDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = "%PDF-print".toByteArray(Charsets.US_ASCII)
        val outFile = File(context.cacheDir, "print-test.pdf")
        val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_WRITE)
        var finishedPages: Array<PageRange>? = null
        val callback = object : PrintDocumentAdapter.WriteResultCallback() {
            override fun onWriteFinished(pages: Array<PageRange>) {
                finishedPages = pages
            }

            override fun onWriteFailed(error: CharSequence) {
                fail("write failed: $error")
            }
        }
        try {
            pdfPrintAdapter("Invoice INV-1", bytes).onWrite(
                arrayOf(PageRange.ALL_PAGES),
                pfd,
                CancellationSignal(),
                callback,
            )
            assertNotNull(finishedPages)
            assertEquals(1, finishedPages!!.size)
            assertArrayEquals(bytes, outFile.readBytes())
        } finally {
            pfd.close()
            outFile.delete()
        }
    }
}
