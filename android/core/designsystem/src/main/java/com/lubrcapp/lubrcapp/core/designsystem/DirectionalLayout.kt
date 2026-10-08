package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Creates logical start/end padding so callers remain RTL-safe.
 * Avoid left/right padding in shared components.
 */
fun directionalPadding(
    start: Dp,
    top: Dp = 0.dp,
    end: Dp,
    bottom: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom,
)
