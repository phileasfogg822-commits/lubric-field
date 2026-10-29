package com.lubrcapp.lubrcapp.feature.printing

import androidx.annotation.StringRes

/**
 * Phase 32.5 — a print theme: the Vyapar-style colour choice for a
 * themed printout. A theme is a RENDER choice, never document data:
 * it is passed to the themed preview and the themed PDF writer, and
 * is never stored in [PrintableDocument]. Colours are ARGB Longs so
 * both renderers share one value (Compose Color(argb) on screen,
 * argb.toInt() on the PDF canvas).
 */
data class PrintTheme(
    val id: String,
    @StringRes val nameRes: Int,
    val argb: Long,
)

val PRINT_THEMES: List<PrintTheme> = listOf(
    PrintTheme("blue", R.string.print_theme_blue, 0xFF1E88E5),
    PrintTheme("orange", R.string.print_theme_orange, 0xFFF4511E),
    PrintTheme("red", R.string.print_theme_red, 0xFFD32F2F),
    PrintTheme("green", R.string.print_theme_green, 0xFF43A047),
    PrintTheme("purple", R.string.print_theme_purple, 0xFF7B1FA2),
)

fun printThemeById(id: String): PrintTheme =
    PRINT_THEMES.firstOrNull { it.id == id } ?: PRINT_THEMES.first()

/**
 * Initials for a header badge: the first letters of the first two
 * words, uppercased; blank names show "?". Same rule as the party
 * avatar's helper, kept here because this module has no dependency
 * on the parties module.
 */
fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = words[0].first().uppercaseChar()
    if (words.size == 1) return first.toString()
    return "$first${words[1].first().uppercaseChar()}"
}
