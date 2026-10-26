package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvWriterTest {

    @Test
    fun writesSimpleTableWithCrlfEndings() {
        val out = CsvWriter.write(
            listOf("Name", "Price"),
            listOf(listOf("Shell HX7", "500")),
        )
        assertEquals("Name,Price\r\nShell HX7,500\r\n", out)
    }

    @Test
    fun quotesFieldContainingComma() {
        val out = CsvWriter.write(
            listOf("Name"),
            listOf(listOf("Ali, Autos")),
        )
        assertEquals("Name\r\n\"Ali, Autos\"\r\n", out)
    }

    @Test
    fun doublesInnerQuotes() {
        val out = CsvWriter.write(
            listOf("Note"),
            listOf(listOf("Say \"hi\"")),
        )
        assertEquals("Note\r\n\"Say \"\"hi\"\"\"\r\n", out)
    }

    @Test
    fun quotesFieldContainingNewline() {
        val out = CsvWriter.write(
            listOf("Note"),
            listOf(listOf("line1\nline2")),
        )
        assertEquals("Note\r\n\"line1\nline2\"\r\n", out)
    }

    @Test
    fun writesEmptyFieldUnquoted() {
        val out = CsvWriter.write(
            listOf("Name", "Phone"),
            listOf(listOf("Ali Autos", "")),
        )
        assertEquals("Name,Phone\r\nAli Autos,\r\n", out)
    }

    @Test
    fun writesHeadersOnlyWhenNoRows() {
        assertEquals("Name,Price\r\n", CsvWriter.write(listOf("Name", "Price"), emptyList()))
    }

    @Test
    fun throwsOnRowWidthMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvWriter.write(listOf("A", "B"), listOf(listOf("only-one")))
        }
    }

    @Test
    fun throwsOnEmptyHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvWriter.write(emptyList(), emptyList())
        }
    }
}
