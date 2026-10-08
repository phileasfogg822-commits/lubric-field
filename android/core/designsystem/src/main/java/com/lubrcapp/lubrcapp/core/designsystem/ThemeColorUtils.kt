package com.lubrcapp.lubrcapp.core.designsystem

internal const val ARGB_WHITE: Int = -0x1
internal const val ARGB_BLACK: Int = -0x1000000

internal fun blendArgb(
    color: Int,
    target: Int,
    targetRatio: Float,
): Int {
    val ratio = targetRatio.coerceIn(0f, 1f)
    fun channel(value: Int, shift: Int): Int = (value ushr shift) and 0xFF
    fun mix(a: Int, b: Int): Int = (a + ((b - a) * ratio)).toInt().coerceIn(0, 255)

    val alpha = mix(channel(color, 24), channel(target, 24))
    val red = mix(channel(color, 16), channel(target, 16))
    val green = mix(channel(color, 8), channel(target, 8))
    val blue = mix(channel(color, 0), channel(target, 0))

    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

internal fun contrastColor(@androidx.annotation.ColorInt background: Int): Int {
    fun linear(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
    }

    val red = linear((background ushr 16) and 0xFF)
    val green = linear((background ushr 8) and 0xFF)
    val blue = linear(background and 0xFF)
    val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue

    return if (luminance > 0.45) ARGB_BLACK else ARGB_WHITE
}
