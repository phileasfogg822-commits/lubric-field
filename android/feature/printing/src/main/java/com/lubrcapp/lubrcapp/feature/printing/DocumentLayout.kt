package com.lubrcapp.lubrcapp.feature.printing

/**
 * Phase 25.2 — the document layout engine: the pure
 * layer beneath every renderer. It turns a
 * PrintableDocument into positioned lines grouped
 * into pages. Alignment is semantic (LEFT / CENTER /
 * RIGHT) — the renderer resolves it to an x position,
 * because text measurement needs framework font
 * metrics this layer deliberately does not touch.
 * Lines flow top to bottom; a line that would pass
 * the content bottom spills to a new page. Line
 * height is size × 1.25. No wrapping: one line per
 * item, however long — wrapping needs measurement
 * and belongs to a renderer, not this layer.
 */
enum class LayoutAlign {
    LEFT,
    CENTER,
    RIGHT,
}

data class LayoutLine(
    val text: String,
    val align: LayoutAlign,
    val y: Float,
    val size: Float,
    val bold: Boolean,
)

data class LayoutPage(
    val lines: List<LayoutLine>,
)

data class DocumentLayout(
    val pages: List<LayoutPage>,
)

fun layoutDocument(
    document: PrintableDocument,
    pageHeight: Float = 842f,
    marginTop: Float = 48f,
    marginBottom: Float = 48f,
    titleSize: Float = 20f,
    bodySize: Float = 12f,
): DocumentLayout {
    val contentBottom = pageHeight - marginBottom
    val pages = mutableListOf<MutableList<LayoutLine>>()
    var current = mutableListOf<LayoutLine>()
    var y = marginTop
    fun emit(text: String, align: LayoutAlign, size: Float, bold: Boolean) {
        val height = size * 1.25f
        if (current.isNotEmpty() && y + height > contentBottom) {
            pages += current
            current = mutableListOf()
            y = marginTop
        }
        current += LayoutLine(text, align, y, size, bold)
        y += height
    }
    emit(document.title, LayoutAlign.CENTER, titleSize, true)
    emit("No: ${document.documentNumber}    Date: ${document.date}", LayoutAlign.LEFT, bodySize, false)
    if (document.partyName != null) {
        emit("Party: ${document.partyName}", LayoutAlign.LEFT, bodySize, false)
    }
    emit("", LayoutAlign.LEFT, bodySize, false)
    document.lines.forEach { line ->
        emit(
            "${line.description}  ${line.quantity.toPlainString()} x " +
                "${line.rate.toPlainString()} = ${line.amount.toPlainString()}",
            LayoutAlign.LEFT,
            bodySize,
            false,
        )
    }
    emit("Subtotal: ${document.subtotal.toPlainString()}", LayoutAlign.RIGHT, bodySize, false)
    if (document.taxTotal.signum() != 0) {
        emit("Tax: ${document.taxTotal.toPlainString()}", LayoutAlign.RIGHT, bodySize, false)
    }
    if (document.discountTotal.signum() != 0) {
        emit("Discount: ${document.discountTotal.toPlainString()}", LayoutAlign.RIGHT, bodySize, false)
    }
    emit("Total: ${document.grandTotal.toPlainString()}", LayoutAlign.RIGHT, bodySize, true)
    if (document.notes != null) {
        emit("Notes: ${document.notes}", LayoutAlign.LEFT, bodySize, false)
    }
    if (document.footer != null) {
        emit(document.footer, LayoutAlign.LEFT, bodySize, false)
    }
    if (current.isNotEmpty()) {
        pages += current
    }
    return DocumentLayout(pages.map { LayoutPage(it) })
}
