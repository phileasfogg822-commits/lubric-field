package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class DesignTokens(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val cardStyle: AppCardStyle,
    val buttonStyle: AppButtonStyle,
    val cardCornerRadius: Dp,
    val buttonCornerRadius: Dp,
    val minTouchTarget: Dp,
    val contentPadding: Dp,
)

internal val LocalDesignTokens = staticCompositionLocalOf<DesignTokens> {
    error("DesignTokens are only available inside LubrcappTheme")
}

object LubrcTheme {
    val tokens: DesignTokens
        @Composable
        get() = LocalDesignTokens.current
}

internal fun designTokens(
    theme: ResolvedDesignTheme,
    darkTheme: Boolean,
): DesignTokens {
    val palette = theme.palette
    val accent = if (darkTheme) {
        blendArgb(palette.accent, ARGB_WHITE, 0.24f)
    } else {
        palette.accent
    }
    val success = if (darkTheme) {
        blendArgb(palette.success, ARGB_WHITE, 0.22f)
    } else {
        palette.success
    }
    val warning = if (darkTheme) {
        blendArgb(palette.warning, ARGB_WHITE, 0.12f)
    } else {
        palette.warning
    }

    return DesignTokens(
        accent = Color(accent),
        onAccent = Color(contrastColor(accent)),
        success = Color(success),
        onSuccess = Color(contrastColor(success)),
        warning = Color(warning),
        onWarning = Color(contrastColor(warning)),
        cardStyle = theme.cardStyle,
        buttonStyle = theme.buttonStyle,
        cardCornerRadius = theme.borderRadiusDp.dp,
        buttonCornerRadius = theme.borderRadiusDp.coerceAtMost(32).dp,
        minTouchTarget = if (theme.largeTouchTargets) 56.dp else 48.dp,
        contentPadding = if (theme.largeTouchTargets) 20.dp else 16.dp,
    )
}
