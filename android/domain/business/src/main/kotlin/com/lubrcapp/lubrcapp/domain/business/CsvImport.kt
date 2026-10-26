package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 28.3 — CSV import (§166–§168). Pure import
 * pipeline over text: parse, map columns to app
 * fields, warn on duplicates. The caller turns the
 * mapped rows into records through existing
 * repository machinery — this package writes
 * nothing, so no whitelist change and no posting
 * side effects live here.
 *
 * Parser rules (mirror of 28.2's writer):
 * - A double quote opens a quoted field only at
 *   field start; inside quotes, two quotes mean one
 *   literal quote. Characters after a closing quote
 *   join the field; a quote mid-field is literal.
 * - CRLF and LF both end rows; a final row needs
 *   no trailing newline.
 * - Blank text, an empty header row, a row whose
 *   width differs from the headers, and an
 *   unterminated quoted field all throw: a
 *   malformed import is rejected, not guessed at.
 *
 * Mapping (§167): source columns match headers by
 * exact text; unmapped columns are ignored; a
 * mapped column missing from the headers, or a
 * duplicated header, throws.
 *
 * Duplicates (§168) are WARNINGS, not rejects:
 * the spec says "warn on possible duplicate" for
 * keys like Mobile, Tax Number, SKU, Barcode and
 * Customer Name. The caller picks the key fields;
 * a later row sharing an earlier row's key tuple
 * produces a warning naming both rows. Rows whose
 * key values are all blank are never warned —
 * blank is absence of signal, not a match.
 */
data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

object CsvParser {
    fun parse(text: String): CsvTable {
        require(text.isNotBlank()) { "CSV text needs a header row" }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i += 1
                    }
                    else -> {
                        field.append(c)
                        i += 1
                    }
                }
                c == '"' && field.isEmpty() -> {
                    inQuotes = true
                    i += 1
                }
                c == ',' -> {
                    row.add(field.toString())
                    field.clear()
                    i += 1
                }
                c == '\r' -> {
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                    i += if (i + 1 < text.length && text[i + 1] == '\n') 2 else 1
                }
                c == '\n' -> {
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                    i += 1
                }
                else -> {
                    field.append(c)
                    i += 1
                }
            }
        }
        if (inQuotes) throw IllegalArgumentException("Unterminated quoted field")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        require(rows.isNotEmpty()) { "CSV text needs a header row" }
        val headers = rows.first()
        require(headers.isNotEmpty()) { "CSV headers must not be empty" }
        val body = rows.drop(1)
        body.forEach { bodyRow ->
            require(bodyRow.size == headers.size) {
                "Row width ${bodyRow.size} must match header width ${headers.size}"
            }
        }
        return CsvTable(headers, body)
    }
}

data class ColumnMapping(
    val sourceColumn: String,
    val targetField: String,
)

object ImportMapper {
    fun map(table: CsvTable, mappings: List<ColumnMapping>): List<Map<String, String>> {
        val indexByHeader = mutableMapOf<String, Int>()
        table.headers.forEachIndexed { idx, header ->
            require(indexByHeader.put(header, idx) == null) { "Duplicate header: $header" }
        }
        val resolved = mappings.map { mapping ->
            val idx = indexByHeader[mapping.sourceColumn]
                ?: throw IllegalArgumentException("Mapped column missing: ${mapping.sourceColumn}")
            mapping.targetField to idx
        }
        return table.rows.map { row ->
            resolved.associate { (field, idx) -> field to row[idx] }
        }
    }
}

data class DuplicateWarning(
    val rowIndex: Int,
    val firstIndex: Int,
    val keyValues: Map<String, String>,
)

object ImportDuplicates {
    fun warnings(rows: List<Map<String, String>>, keyFields: List<String>): List<DuplicateWarning> {
        require(keyFields.isNotEmpty()) { "Duplicate check needs at least one key field" }
        val seen = mutableMapOf<List<String>, Int>()
        val out = mutableListOf<DuplicateWarning>()
        rows.forEachIndexed { idx, row ->
            val values = keyFields.map { key ->
                row[key] ?: throw IllegalArgumentException("Key field missing: $key")
            }
            if (values.all { it.isBlank() }) return@forEachIndexed
            val first = seen.putIfAbsent(values, idx)
            if (first != null) {
                out.add(DuplicateWarning(idx, first, keyFields.zip(values).toMap()))
            }
        }
        return out
    }
}
