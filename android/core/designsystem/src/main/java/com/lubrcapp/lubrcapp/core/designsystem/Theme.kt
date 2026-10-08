package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LubrcappTheme(
    config: DesignSystemConfig,
    preferences: ThemePreferences = ThemePreferences(),
    forceDarkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val resolved = remember(config, preferences) { config.resolve(preferences) }
    val darkTheme = forceDarkTheme ?: when (resolved.themeMode) {
        DesignThemeMode.LIGHT -> false
        DesignThemeMode.DARK -> true
        DesignThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = remember(resolved.palette, darkTheme) {
        if (darkTheme) darkScheme(resolved.palette) else lightScheme(resolved.palette)
    }
    val typography = remember(resolved.fontFamilyName, resolved.textScale) {
        runtimeTypography(
            fontFamilyName = resolved.fontFamilyName,
            textScale = resolved.textScale,
        )
    }
    val shapes = remember(resolved.borderRadiusDp) {
        runtimeShapes(resolved.borderRadiusDp)
    }
    val tokens = remember(resolved, darkTheme) {
        designTokens(resolved, darkTheme)
    }

    CompositionLocalProvider(LocalDesignTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

private fun lightScheme(palette: BrandPalette) = lightColorScheme(
    primary = Color(palette.primary),
    onPrimary = Color(contrastColor(palette.primary)),
    primaryContainer = Color(blendArgb(palette.primary, ARGB_WHITE, 0.86f)),
    onPrimaryContainer = Color(
        contrastColor(blendArgb(palette.primary, ARGB_WHITE, 0.86f))
    ),
    secondary = Color(palette.secondary),
    onSecondary = Color(contrastColor(palette.secondary)),
    secondaryContainer = Color(blendArgb(palette.secondary, ARGB_WHITE, 0.86f)),
    onSecondaryContainer = Color(
        contrastColor(blendArgb(palette.secondary, ARGB_WHITE, 0.86f))
    ),
    tertiary = Color(palette.accent),
    onTertiary = Color(contrastColor(palette.accent)),
    tertiaryContainer = Color(blendArgb(palette.accent, ARGB_WHITE, 0.86f)),
    onTertiaryContainer = Color(
        contrastColor(blendArgb(palette.accent, ARGB_WHITE, 0.86f))
    ),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(palette.error),
    onError = Color(contrastColor(palette.error)),
    outline = Color(0xFF79747E),
    scrim = Color(0x99000000),
)

private fun darkScheme(palette: BrandPalette): androidx.compose.material3.ColorScheme {
    val primary = blendArgb(palette.primary, ARGB_WHITE, 0.30f)
    val secondary = blendArgb(palette.secondary, ARGB_WHITE, 0.24f)
    val accent = blendArgb(palette.accent, ARGB_WHITE, 0.24f)
    val error = blendArgb(palette.error, ARGB_WHITE, 0.20f)

    return darkColorScheme(
        primary = Color(primary),
        onPrimary = Color(contrastColor(primary)),
        primaryContainer = Color(blendArgb(palette.primary, ARGB_BLACK, 0.38f)),
        onPrimaryContainer = Color(
            contrastColor(blendArgb(palette.primary, ARGB_BLACK, 0.38f))
        ),
        secondary = Color(secondary),
        onSecondary = Color(contrastColor(secondary)),
        secondaryContainer = Color(blendArgb(palette.secondary, ARGB_BLACK, 0.38f)),
        onSecondaryContainer = Color(
            contrastColor(blendArgb(palette.secondary, ARGB_BLACK, 0.38f))
        ),
        tertiary = Color(accent),
        onTertiary = Color(contrastColor(accent)),
        tertiaryContainer = Color(blendArgb(palette.accent, ARGB_BLACK, 0.38f)),
        onTertiaryContainer = Color(
            contrastColor(blendArgb(palette.accent, ARGB_BLACK, 0.38f))
        ),
        background = Color(0xFF121212),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        error = Color(error),
        onError = Color(contrastColor(error)),
        outline = Color(0xFF938F99),
        scrim = Color(0x99000000),
    )
}

private fun runtimeShapes(borderRadiusDp: Int): Shapes {
    val radius = borderRadiusDp.coerceIn(0, 64)
    return Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(
            (radius / 3).dpValue()
        ),
        small = androidx.compose.foundation.shape.RoundedCornerShape(
            (radius / 2).dpValue()
        ),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(radius.dpValue()),
        large = androidx.compose.foundation.shape.RoundedCornerShape(
            (radius + 4).coerceAtMost(64).dpValue()
        ),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(
            (radius + 12).coerceAtMost(64).dpValue()
        ),
    )
}

private fun Int.dpValue() = this.dp

private fun runtimeTypography(
    fontFamilyName: String?,
    textScale: Float,
): Typography {
    val base = Typography()
    val family = runtimeFontFamily(fontFamilyName)

    fun scaled(style: TextStyle): TextStyle = style.copy(
        fontFamily = family,
        fontSize = (style.fontSize.value * textScale).sp,
        lineHeight = (style.lineHeight.value * textScale).sp,
    )

    return base.copy(
        displayLarge = scaled(base.displayLarge),
        displayMedium = scaled(base.displayMedium),
        displaySmall = scaled(base.displaySmall),
        headlineLarge = scaled(base.headlineLarge),
        headlineMedium = scaled(base.headlineMedium),
        headlineSmall = scaled(base.headlineSmall),
        titleLarge = scaled(base.titleLarge),
        titleMedium = scaled(base.titleMedium),
        titleSmall = scaled(base.titleSmall),
        bodyLarge = scaled(base.bodyLarge),
        bodyMedium = scaled(base.bodyMedium),
        bodySmall = scaled(base.bodySmall),
        labelLarge = scaled(base.labelLarge),
        labelMedium = scaled(base.labelMedium),
        labelSmall = scaled(base.labelSmall),
    )
}

private fun runtimeFontFamily(name: String?): FontFamily =
    when (name?.trim()?.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans", "sans-serif", "sansserif" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
