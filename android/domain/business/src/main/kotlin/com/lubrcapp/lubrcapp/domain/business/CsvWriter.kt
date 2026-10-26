package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 28.2 — CSV export (§86). Pure writer over
 * tabular rows: headers plus rows of plain strings.
 * The caller formats numbers and dates before
 * handing them over — this writer only escapes.
 *
 * Rules (RFC 4180 shape, pinned by tests):
 * - A field containing a comma, a double quote, a
 *   newline or a carriage return is wrapped in
 *   double quotes, with inner quotes doubled.
 * - Every other field is written as-is, including
 *   empty fields (no quotes added).
 * - Lines end with CRLF, including the last line.
 * - Every row must match the header width, and
 *   headers must be non-empty — a misaligned
 *   table is rejected, not silently written, because
 *   a shifted column corrupts an import (§167)
 *   downstream.
 */
object CsvWriter {
    fun write(headers: List<String>, rows: List<List<String>>): String {
        require(headers.isNotEmpty()) { "CSV needs at least one header" }
        rows.forEach { row ->
            require(row.size == headers.size) {
                "Row width ${row.size} must match header width ${headers.size}"
            }
        }
        val lines = listOf(headers) + rows
        return lines.joinToString("\r\n") { line ->
            line.joinToString(",") { escape(it) }
        } + "\r\n"
    }

    private fun escape(field: String): String {
        if (field.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }
}
