package com.lubrcapp.lubrcapp.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDownloadHelperTest {

    @Test
    fun downloadedPdfIsReadableBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = "%PDF-download".toByteArray(Charsets.US_ASCII)
        val uri = downloadPdf(context, "download-test.pdf", bytes)
        assertNotNull(uri)
        try {
            val readBack = context.contentResolver.openInputStream(uri!!)?.use { it.readBytes() }
            assertTrue(readBack.contentEquals(bytes))
        } finally {
            context.contentResolver.delete(uri!!, null, null)
        }
    }

    @Test
    fun sameFileNameDownloadsTwiceWithoutOverwriting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = downloadPdf(context, "download-twice.pdf", byteArrayOf(1))
        val second = downloadPdf(context, "download-twice.pdf", byteArrayOf(2))
        try {
            assertNotNull(first)
            assertNotNull(second)
            assertTrue(first != second)
        } finally {
            if (first != null) context.contentResolver.delete(first, null, null)
            if (second != null) context.contentResolver.delete(second, null, null)
        }
    }
}
