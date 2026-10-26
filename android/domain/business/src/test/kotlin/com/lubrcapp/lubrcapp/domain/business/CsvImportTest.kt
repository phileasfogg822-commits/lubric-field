package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {

    @Test
    fun parsesSimpleTable() {
        val table = CsvParser.parse("Name,Price\r\nShell HX7,500\r\n")
        assertEquals(listOf("Name", "Price"), table.headers)
        assertEquals(listOf(listOf("Shell HX7", "500")), table.rows)
    }

    @Test
    fun parsesQuotedCommaAndDoubledQuotes() {
        val table = CsvParser.parse("Name,Note\r\n\"Ali, Autos\",\"Say \"\"hi\"\"\"\r\n")
        assertEquals(listOf(listOf("Ali, Autos", "Say \"hi\"")), table.rows)
    }

    @Test
    fun parsesLfEndingsWithoutTrailingNewline() {
        val table = CsvParser.parse("A,B\n1,2")
        assertEquals(listOf("A", "B"), table.headers)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    @Test
    fun parsesEmptyFields() {
        val table = CsvParser.parse("A,B,C\r\n,,\r\n")
        assertEquals(listOf(listOf("", "", "")), table.rows)
    }

    @Test
    fun throwsOnUnterminatedQuote() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvParser.parse("A\r\n\"oops\r\n")
        }
    }

    @Test
    fun throwsOnRowWidthMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvParser.parse("A,B\r\n1\r\n")
        }
    }

    @Test
    fun mapsByHeaderNameIgnoringUnmapped() {
        val table = CsvTable(
            headers = listOf("Product Name", "Code", "Qty"),
            rows = listOf(listOf("Shell HX7", "HX7", "10")),
        )
        val mapped = ImportMapper.map(
            table,
            listOf(
                ColumnMapping("Product Name", "Name"),
                ColumnMapping("Code", "SKU"),
            ),
        )
        assertEquals(listOf(mapOf("Name" to "Shell HX7", "SKU" to "HX7")), mapped)
    }

    @Test
    fun throwsOnMissingMappedColumn() {
        val table = CsvTable(headers = listOf("A"), rows = listOf(listOf("1")))
        assertThrows(IllegalArgumentException::class.java) {
            ImportMapper.map(table, listOf(ColumnMapping("Nope", "X")))
        }
    }

    @Test
    fun throwsOnDuplicateHeader() {
        val table = CsvTable(headers = listOf("A", "A"), rows = listOf(listOf("1", "2")))
        assertThrows(IllegalArgumentException::class.java) {
            ImportMapper.map(table, listOf(ColumnMapping("A", "X")))
        }
    }

    @Test
    fun warnsSecondOccurrenceWithFirstIndex() {
        val warnings = ImportDuplicates.warnings(
            listOf(mapOf("SKU" to "X"), mapOf("SKU" to "Y"), mapOf("SKU" to "X")),
            listOf("SKU"),
        )
        assertEquals(1, warnings.size)
        assertEquals(2, warnings.single().rowIndex)
        assertEquals(0, warnings.single().firstIndex)
        assertEquals(mapOf("SKU" to "X"), warnings.single().keyValues)
    }

    @Test
    fun multiFieldKeysNeedFullTupleMatch() {
        val warnings = ImportDuplicates.warnings(
            listOf(
                mapOf("Mobile" to "1", "SKU" to "A"),
                mapOf("Mobile" to "1", "SKU" to "B"),
            ),
            listOf("Mobile", "SKU"),
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun blankKeysAreNeverWarned() {
        val warnings = ImportDuplicates.warnings(
            listOf(mapOf("SKU" to ""), mapOf("SKU" to "")),
            listOf("SKU"),
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun noDuplicatesMeansNoWarnings() {
        val warnings = ImportDuplicates.warnings(
            listOf(mapOf("SKU" to "A"), mapOf("SKU" to "B")),
            listOf("SKU"),
        )
        assertTrue(warnings.isEmpty())
    }
}
