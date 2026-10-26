package com.lubrcapp.lubrcapp.shell

import android.os.ParcelFileDescriptor
import android.print.PrintDocumentInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The adapter's framework callbacks cannot be subclassed
 * outside android.print (package-private constructors), so
 * these tests exercise the adapter's two testable
 * functions directly: the document info it reports, and
 * the byte copy it performs. The callback plumbing itself
 * is thin glue, covered by the same reasoning as the
 * share/download content wiring.
 */
@RunWith(AndroidJUnit4::class)
class PdfPrintHelperTest {

    @Test
    fun printDocumentInfoReportsDocumentJob() {
        val info = printDocumentInfo("Invoice INV-1")
        assertEquals("Invoice INV-1", info.name)
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, info.contentType)
        assertEquals(PrintDocumentInfo.PAGE_COUNT_UNKNOWN, info.pageCount)
    }

    @Test
    fun copyBytesToFileDescriptorWritesBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = "%PDF-print".toByteArray(Charsets.US_ASCII)
        val outFile = File(context.cacheDir, "print-test.pdf")
        val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_WRITE)
        try {
            copyBytesToFileDescriptor(bytes, pfd)
            assertArrayEquals(bytes, outFile.readBytes())
        } finally {
            pfd.close()
            outFile.delete()
        }
    }
}
