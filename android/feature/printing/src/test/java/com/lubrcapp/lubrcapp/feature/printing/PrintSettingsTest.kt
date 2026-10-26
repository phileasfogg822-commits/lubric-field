package com.lubrcapp.lubrcapp.feature.printing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintSettingsTest {

    private fun esc(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toByteArray()

    private fun ascii(value: String): ByteArray =
        value.map { it.code.toByte() }.toByteArray()

    private fun line(text: String): LayoutLine =
        LayoutLine(text, LayoutAlign.LEFT, 0f, 12f, false)

    private fun layout(vararg pages: List<LayoutLine>): DocumentLayout =
        DocumentLayout(pages.map { LayoutPage(it) })

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        for (i in from..haystack.size - needle.size) {
            if (haystack.sliceArray(i until i + needle.size).contentEquals(needle)) return i
        }
        return -1
    }

    private fun countOf(haystack: ByteArray, needle: ByteArray): Int {
        var count = 0
        var from = 0
        while (true) {
            val i = indexOf(haystack, needle, from)
            if (i < 0) return count
            count++
            from = i + needle.size
        }
    }

    @Test
    fun defaultsAre80mmWithAutoCut() {
        val settings = PrintSettings()
        assertEquals(PaperWidth.MM_80, settings.paperWidth)
        assertEquals(42, settings.paperWidth.widthChars)
        assertTrue(settings.autoCut)
    }

    @Test
    fun narrowPaperCarries32Columns() {
        assertEquals(32, PaperWidth.MM_58.widthChars)
    }

    @Test
    fun autoCutOffOmitsTheCutAndEndsWithLineFeed() {
        val bytes = thermalReceipt(
            layout(listOf(line("Hello"))),
            autoCut = false,
        )
        assertEquals(0, countOf(bytes, esc(0x1D, 0x56, 0x00)))
        assertEquals(0x0A.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun settingsDriveWidthAndCutEndToEnd() {
        val settings = PrintSettings(paperWidth = PaperWidth.MM_58, autoCut = true)
        val bytes = thermalReceipt(
            layout(listOf(line("A".repeat(50)))),
            widthChars = settings.paperWidth.widthChars,
            autoCut = settings.autoCut,
        )
        // ESC @ (2) + ESC a 0 (3) + ESC E 0 (3) + text (32) + LF (1) + cut (3) = 44.
        assertEquals(44, bytes.size)
        assertArrayEquals(ascii("A".repeat(32)), bytes.sliceArray(8 until 40))
    }
}
