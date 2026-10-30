package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 33.1 — width breakpoints for responsive layouts.
 * Compact is a phone single column; medium is a large phone or
 * small tablet; expanded is a full tablet. The classifier is a
 * pure function of the width so it is unit-testable; callers
 * read the width from BoxWithConstraints and pass it in, which
 * keeps this module free of any window-size dependency.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

fun windowWidthClass(widthDp: Float): WindowWidthClass =
    when {
        widthDp < 600f -> WindowWidthClass.COMPACT
        widthDp < 840f -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }

fun windowWidthClass(width: Dp): WindowWidthClass = windowWidthClass(width.value)

val WindowWidthClass.isTablet: Boolean
    get() = this != WindowWidthClass.COMPACT

fun Dp.toWindowWidthClass(): WindowWidthClass = windowWidthClass(this)
