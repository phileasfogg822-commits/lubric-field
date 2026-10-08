package com.lubrcapp.lubrcapp.core.designsystem

import androidx.annotation.ColorInt

enum class DesignThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class AppCardStyle {
    DEFAULT,
    FLAT,
    OUTLINED;

    companion object {
        fun parse(value: String?): AppCardStyle =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class AppButtonStyle {
    ELEVATED,
    FLAT,
    OUTLINED;

    companion object {
        fun parse(value: String?): AppButtonStyle =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: ELEVATED
    }
}

data class BrandPalette(
    @ColorInt val primary: Int,
    @ColorInt val secondary: Int,
    @ColorInt val accent: Int,
    @ColorInt val success: Int,
    @ColorInt val warning: Int,
    @ColorInt val error: Int,
)

data class DesignSystemConfig(
    val palette: BrandPalette,
    val fontFamilyName: String? = null,
    val cardStyle: AppCardStyle = AppCardStyle.DEFAULT,
    val buttonStyle: AppButtonStyle = AppButtonStyle.ELEVATED,
    val borderRadiusDp: Int = 16,
    val themeMode: DesignThemeMode = DesignThemeMode.SYSTEM,
    val defaultTextScale: Float = 1f,
    val defaultLargeTouchTargets: Boolean = false,
) {
    init {
        require(borderRadiusDp in 0..64) { "borderRadiusDp must be between 0 and 64" }
        require(defaultTextScale in MIN_TEXT_SCALE..MAX_TEXT_SCALE) {
            "defaultTextScale must be between $MIN_TEXT_SCALE and $MAX_TEXT_SCALE"
        }
    }
}

data class ThemePreferences(
    val themeModeOverride: DesignThemeMode? = null,
    val textScaleOverride: Float? = null,
    val largeTouchTargetsOverride: Boolean? = null,
) {
    init {
        require(textScaleOverride == null || textScaleOverride in MIN_TEXT_SCALE..MAX_TEXT_SCALE) {
            "textScaleOverride must be between $MIN_TEXT_SCALE and $MAX_TEXT_SCALE"
        }
    }
}

data class ResolvedDesignTheme(
    val palette: BrandPalette,
    val fontFamilyName: String?,
    val cardStyle: AppCardStyle,
    val buttonStyle: AppButtonStyle,
    val borderRadiusDp: Int,
    val themeMode: DesignThemeMode,
    val textScale: Float,
    val largeTouchTargets: Boolean,
)

fun DesignSystemConfig.resolve(preferences: ThemePreferences): ResolvedDesignTheme =
    ResolvedDesignTheme(
        palette = palette,
        fontFamilyName = fontFamilyName,
        cardStyle = cardStyle,
        buttonStyle = buttonStyle,
        borderRadiusDp = borderRadiusDp,
        themeMode = preferences.themeModeOverride ?: themeMode,
        textScale = preferences.textScaleOverride ?: defaultTextScale,
        largeTouchTargets = preferences.largeTouchTargetsOverride ?: defaultLargeTouchTargets,
    )

const val MIN_TEXT_SCALE = 0.85f
const val MAX_TEXT_SCALE = 1.50f
