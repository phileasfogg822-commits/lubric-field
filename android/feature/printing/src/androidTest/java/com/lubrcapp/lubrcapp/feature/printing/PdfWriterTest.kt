package com.lubrcapp.lubrcapp.feature.printing

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfWriterTest {

    @Test
    fun writerProducesValidPdfBytes() {
        val document = printableDocument(
            title = "Invoice",
            documentNumber = "INV-1",
            date = "2026-10-25",
            lines = listOf(
                PrintableLine("Oil 1L", BigDecimal.ONE, BigDecimal("500"), BigDecimal("500")),
            ),
        )
        val layout = layoutDocument(document)
        val output = ByteArrayOutputStream()
        writePdf(layout, 595, 842, output)
        val bytes = output.toByteArray()
        assertTrue(bytes.isNotEmpty())
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
    }
}
