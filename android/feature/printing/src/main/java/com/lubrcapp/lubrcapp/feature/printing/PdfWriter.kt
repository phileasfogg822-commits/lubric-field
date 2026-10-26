package com.lubrcapp.lubrcapp.feature.printing

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Phase 25.3 — the PDF writer: renders a
 * [DocumentLayout] to PDF with the platform
 * PdfDocument API (no new dependency). X positions
 * resolve from the layout's semantic alignment via
 * [resolveX]; the baseline is the line top minus the
 * Paint ascent (ascent is negative), so text sits
 * inside its laid-out row. Contract: layout dims
 * and PDF dims share the same point space — pass
 * the same pageHeight used for layoutDocument, and
 * the pageWidth the renderer chose. Layouts from
 * layoutDocument always carry at least one page
 * (the title), so the document is never empty. The
 * PdfDocument is closed after writing, even on
 * failure.
 */
fun resolveX(
    align: LayoutAlign,
    textWidth: Float,
    pageWidth: Float,
    marginLeft: Float,
    marginRight: Float,
): Float = when (align) {
    LayoutAlign.LEFT -> marginLeft
    LayoutAlign.CENTER -> (pageWidth - textWidth) / 2f
    LayoutAlign.RIGHT -> pageWidth - marginRight - textWidth
}

fun writePdf(
    layout: DocumentLayout,
    pageWidth: Int,
    pageHeight: Int,
    output: OutputStream,
    marginLeft: Float = 48f,
    marginRight: Float = 48f,
) {
    val pdf = PdfDocument()
    try {
        layout.pages.forEachIndexed { index, page ->
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val pdfPage = pdf.startPage(info)
            val paint = Paint()
            page.lines.forEach { line ->
                paint.textSize = line.size
                paint.isFakeBoldText = line.bold
                val textWidth = paint.measureText(line.text)
                val x = resolveX(line.align, textWidth, pageWidth.toFloat(), marginLeft, marginRight)
                val baseline = line.y - paint.fontMetrics.ascent
                pdfPage.canvas.drawText(line.text, x, baseline, paint)
            }
            pdf.finishPage(pdfPage)
        }
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}
