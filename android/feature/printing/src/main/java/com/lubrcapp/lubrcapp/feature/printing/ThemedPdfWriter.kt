package com.lubrcapp.lubrcapp.feature.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Phase 32.5 — the themed PDF writer: renders a themed row list to
 * PDF with the platform PdfDocument API (no new dependency). The
 * header block carries the branding Umar asked for: a badge (the
 * photo when one is supplied, else the name's initials in a theme
 * -coloured circle), the big name, an optional detail line, the
 * document title, and a theme rule. Tables get a theme-coloured
 * header band with white text; columns follow the row weights.
 * Pagination happens during drawing: a row that would pass the
 * content bottom starts a new page, and a break inside a table
 * re-emits that table's header first.
 */
fun writeThemedPdf(
    title: String,
    headerName: String,
    headerDetail: String?,
    photo: Bitmap?,
    themeArgb: Int,
    rows: List<ThemedRow>,
    pageWidth: Int,
    pageHeight: Int,
    output: OutputStream,
    margin: Float = 48f,
) {
    val pdf = PdfDocument()
    try {
        val contentWidth = pageWidth - margin * 2
        var pageIndex = 0
        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageIndex).create(),
        )
        var canvas = page.canvas
        var y = margin
        val paint = Paint().apply { isAntiAlias = true }
        val contentBottom = pageHeight - margin

        fun newPage() {
            pdf.finishPage(page)
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageIndex).create(),
            )
            canvas = page.canvas
            y = margin
        }

        fun drawHeader() {
            val badgeSize = 56f
            val badgeCx = margin + badgeSize / 2f
            val badgeCy = y + badgeSize / 2f
            if (photo != null) {
                val scaled = Bitmap.createScaledBitmap(photo, badgeSize.toInt(), badgeSize.toInt(), true)
                canvas.save()
                val path = Path().apply { addCircle(badgeCx, badgeCy, badgeSize / 2f, Path.Direction.CW) }
                canvas.clipPath(path)
                canvas.drawBitmap(scaled, margin, y, null)
                canvas.restore()
                if (scaled != photo) scaled.recycle()
            } else {
                paint.color = themeArgb
                canvas.drawCircle(badgeCx, badgeCy, badgeSize / 2f, paint)
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 22f
                paint.isFakeBoldText = true
                val initials = initialsFor(headerName)
                val w = paint.measureText(initials)
                canvas.drawText(
                    initials,
                    badgeCx - w / 2f,
                    badgeCy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f,
                    paint,
                )
            }
            val textX = margin + badgeSize + 12f
            paint.color = themeArgb
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText(headerName, textX, y + 24f, paint)
            val nameBottom = y + badgeSize
            if (headerDetail != null) {
                paint.color = 0xFF000000.toInt()
                paint.textSize = 12f
                paint.isFakeBoldText = false
                canvas.drawText(headerDetail, textX, y + 44f, paint)
            }
            y = nameBottom + 12f
            paint.color = 0xFF000000.toInt()
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText(title, margin, y + 18f, paint)
            y += 18f + 10f
            paint.color = themeArgb
            canvas.drawRect(margin, y, margin + contentWidth, y + 3f, paint)
            y += 3f + 16f
        }

        fun columnX(weights: List<Float>, index: Int): Float {
            var x = margin
            for (i in 0 until index) x += weights[i] * contentWidth
            return x
        }

        fun columnWidth(weights: List<Float>, index: Int): Float = weights[index] * contentWidth

        var lastTableHeader: ThemedRow.TableHeader? = null
        var inTable = false

        fun drawTableHeader(header: ThemedRow.TableHeader) {
            paint.color = themeArgb
            canvas.drawRect(margin, y, margin + contentWidth, y + 22f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = 12f
            paint.isFakeBoldText = true
            header.columns.forEachIndexed { i, col ->
                val x = columnX(header.weights, i) + 6f
                canvas.drawText(col, x, y + 22f - paint.fontMetrics.descent - 4f, paint)
            }
            y += 22f + 4f
        }

        drawHeader()
        rows.forEach { row ->
            val height = when (row) {
                is ThemedRow.TableHeader -> 26f
                is ThemedRow.TableRow -> 18f
                is ThemedRow.Total -> if (row.emphasized) 22f else 18f
                is ThemedRow.Meta -> 16f
                is ThemedRow.Note -> 16f
                is ThemedRow.Blank -> 12f
            }
            if (y + height > contentBottom) {
                val wasInTable = inTable
                val headerToRepeat = lastTableHeader
                newPage()
                if (wasInTable && headerToRepeat != null) {
                    drawTableHeader(headerToRepeat)
                }
            }
            when (row) {
                is ThemedRow.Meta -> {
                    paint.color = 0xFF000000.toInt()
                    paint.textSize = 12f
                    paint.isFakeBoldText = false
                    canvas.drawText(row.text, margin, y + 12f, paint)
                    inTable = false
                }
                is ThemedRow.Blank -> {
                    inTable = false
                }
                is ThemedRow.TableHeader -> {
                    drawTableHeader(row)
                    lastTableHeader = row
                    inTable = true
                }
                is ThemedRow.TableRow -> {
                    paint.textSize = 12f
                    paint.isFakeBoldText = false
                    paint.color = 0xFF000000.toInt()
                    row.cells.forEachIndexed { i, cell ->
                        val x = columnX(row.weights, i)
                        val w = columnWidth(row.weights, i)
                        if (i >= row.numericFrom) {
                            val tw = paint.measureText(cell)
                            canvas.drawText(cell, x + w - 6f - tw, y + 12f, paint)
                        } else {
                            canvas.drawText(cell, x + 6f, y + 12f, paint)
                        }
                    }
                    inTable = true
                }
                is ThemedRow.Total -> {
                    paint.textSize = if (row.emphasized) 14f else 12f
                    paint.isFakeBoldText = row.emphasized
                    paint.color = if (row.emphasized) themeArgb else 0xFF000000.toInt()
                    val text = "${row.label}: ${row.value}"
                    val tw = paint.measureText(text)
                    canvas.drawText(text, margin + contentWidth - tw, y + 12f, paint)
                    inTable = false
                }
                is ThemedRow.Note -> {
                    paint.color = 0xFF000000.toInt()
                    paint.textSize = 12f
                    paint.isFakeBoldText = false
                    canvas.drawText(row.text, margin, y + 12f, paint)
                    inTable = false
                }
            }
            y += height
        }
        pdf.finishPage(page)
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}
