package com.lubrcapp.lubrcapp.feature.printing

/**
 * Phase 25.4 — the thermal receipt renderer: the
 * ESC/POS half of spec §98's abstraction layer.
 * It renders a DocumentLayout to the byte stream a
 * thermal receipt printer consumes — the same
 * layout writePdf renders to PDF, so every document
 * has two renderers and no second layout pass.
 *
 * Pure Kotlin, no framework classes: the byte
 * stream is fully JVM-testable. Device transport
 * (Bluetooth / USB / network) is deliberately out
 * of this layer — it moves bytes, it does not
 * decide them, and no CI runner can prove it.
 *
 * Commands used: ESC @ (initialize), ESC a n
 * (alignment: 0 LEFT, 1 CENTER, 2 RIGHT),
 * ESC E n (bold on/off), LF (line feed),
 * GS V 0 (full cut). Page boundaries are ignored
 * — receipt paper is continuous, so lines flow as
 * one stream and the cut comes once, at the end.
 * Text is US-ASCII: characters outside ASCII fold
 * to '?', and lines longer than [widthChars] are
 * truncated — a thermal line has a fixed column
 * count and the printer would wrap or lose the
 * overflow unpredictably. QR, logo bitmaps, and
 * the cash-drawer kick are NOT encoded here;
 * they need a bitmap/QR encoder and a device
 * signal the document model does not carry yet.
 */
fun thermalReceipt(
    layout: DocumentLayout,
    widthChars: Int = 42,
): ByteArray {
    require(widthChars > 0) { "widthChars must be positive" }
    val out = mutableListOf<Byte>()
    fun bytes(vararg values: Int) {
        values.forEach { out += it.toByte() }
    }
    fun text(value: String) {
        value.take(widthChars).forEach { ch ->
            out += if (ch.code < 128) ch.code.toByte() else '?'.code.toByte()
        }
    }
    bytes(0x1B, 0x40)
    layout.pages.forEach { page ->
        page.lines.forEach { line ->
            val alignByte = when (line.align) {
                LayoutAlign.LEFT -> 0
                LayoutAlign.CENTER -> 1
                LayoutAlign.RIGHT -> 2
            }
            bytes(0x1B, 0x61, alignByte)
            bytes(0x1B, 0x45, if (line.bold) 1 else 0)
            text(line.text)
            bytes(0x0A)
        }
    }
    bytes(0x1D, 0x56, 0x00)
    return out.toByteArray()
}
