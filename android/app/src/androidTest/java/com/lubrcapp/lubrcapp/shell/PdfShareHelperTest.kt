package com.lubrcapp.lubrcapp.shell

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfShareHelperTest {

    @Test
    fun sharedPdfFileIsWrittenAndUriUsesFileProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = "%PDF-fake".toByteArray(Charsets.US_ASCII)
        val uri = writeSharedPdf(context, "share-test.pdf", bytes)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        val file = File(context.cacheDir, "shared/share-test.pdf")
        assertTrue(file.exists())
        assertTrue(file.readBytes().contentEquals(bytes))
    }

    @Test
    fun shareIntentCarriesPdfStreamAndReadGrant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = writeSharedPdf(context, "share-intent.pdf", byteArrayOf(1, 2, 3))
        val intent = pdfShareIntent(uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
