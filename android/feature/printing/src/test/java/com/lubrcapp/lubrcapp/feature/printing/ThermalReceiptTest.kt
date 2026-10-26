package com.lubrcapp.lubrcapp.feature.printing

import java.math.BigDecimal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalReceiptTest {

    private fun esc(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toByteArray()

    private fun ascii(value: String): ByteArray =
        value.map { it.code.toByte() }.toByteArray()

    private fun line(
        text: String,
        align: LayoutAlign = LayoutAlign.LEFT,
        bold: Boolean = false,
    ): LayoutLine = LayoutLine(text, align, 0f, 12f, bold)

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
    fun startsWithInitializeAndEndsWithCut() {
        val bytes = thermalReceipt(layout(listOf(line("Hello"))))
        assertArrayEquals(esc(0x1B, 0x40), bytes.sliceArray(0 until 2))
        assertArrayEquals(esc(0x1D, 0x56, 0x00), bytes.sliceArray(bytes.size - 3 until bytes.size))
    }

    @Test
    fun alignmentCommandsInOrder() {
        val bytes = thermalReceipt(
            layout(
                listOf(
                    line("L", LayoutAlign.LEFT),
                    line("C", LayoutAlign.CENTER),
                    line("R", LayoutAlign.RIGHT),
                )
            )
        )
        val left = indexOf(bytes, esc(0x1B, 0x61, 0))
        val center = indexOf(bytes, esc(0x1B, 0x61, 1))
        val right = indexOf(bytes, esc(0x1B, 0x61, 2))
        assertTrue(left >= 0 && left < center && center < right)
    }

    @Test
    fun boldLineSwitchesBoldOnAndNextLineSwitchesItOff() {
        val bytes = thermalReceipt(
            layout(listOf(line("B", bold = true), line("P")))
        )
        val boldOn = indexOf(bytes, esc(0x1B, 0x45, 1))
        val boldText = indexOf(bytes, ascii("B"), boldOn)
        val boldOff = indexOf(bytes, esc(0x1B, 0x45, 0), boldText)
        val plainText = indexOf(bytes, ascii("P"), boldOff)
        assertTrue(boldOn >= 0 && boldText > boldOn && boldOff > boldText && plainText > boldOff)
    }

    @Test
    fun longLineTruncatesToWidthChars() {
        val bytes = thermalReceipt(layout(listOf(line("A".repeat(50)))), widthChars = 42)
        // ESC @ (2) + ESC a 0 (3) + ESC E 0 (3) + text (42) + LF (1) + cut (3) = 54.
        assertEquals(54, bytes.size)
        assertArrayEquals(ascii("A".repeat(42)), bytes.sliceArray(8 until 50))
    }

    @Test
    fun nonAsciiFoldsToQuestionMark() {
        val bytes = thermalReceipt(layout(listOf(line("café"))))
        // Same single-line shape as above: text starts at offset 8, 4 bytes long.
        assertArrayEquals(ascii("caf?"), bytes.sliceArray(8 until 12))
    }

    @Test
    fun pagesFlowContinuouslyWithASingleCut() {
        val bytes = thermalReceipt(
            layout(listOf(line("First")), listOf(line("Second")))
        )
        assertEquals(1, countOf(bytes, esc(0x1D, 0x56, 0x00)))
        val first = indexOf(bytes, ascii("First"))
        val second = indexOf(bytes, ascii("Second"), first)
        assertTrue(first >= 0 && second > first)
    }

    @Test
    fun invalidWidthThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            thermalReceipt(layout(listOf(line("Hello"))), widthChars = 0)
        }
    }

    @Test
    fun endToEndDocumentRendersTitleAndTotal() {
        val document = printableDocument(
            title = "Invoice",
            documentNumber = "INV-1",
            date = "2026-10-26",
            lines = listOf(
                PrintableLine("Oil", BigDecimal("2"), BigDecimal("100"), BigDecimal("200"))
            ),
        )
        val bytes = thermalReceipt(layoutDocument(document))
        assertTrue(indexOf(bytes, ascii("Invoice")) >= 0)
        assertTrue(indexOf(bytes, ascii("Total: 200")) >= 0)
    }
}
