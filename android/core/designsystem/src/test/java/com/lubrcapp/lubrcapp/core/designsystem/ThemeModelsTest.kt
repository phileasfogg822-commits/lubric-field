package com.lubrcapp.lubrcapp.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModelsTest {

    private val config = DesignSystemConfig(
        palette = BrandPalette(
            primary = 0xFF112233.toInt(),
            secondary = 0xFF223344.toInt(),
            accent = 0xFF334455.toInt(),
            success = 0xFF006600.toInt(),
            warning = 0xFFFFAA00.toInt(),
            error = 0xFFAA0000.toInt(),
        ),
        fontFamilyName = "serif",
        cardStyle = AppCardStyle.OUTLINED,
        buttonStyle = AppButtonStyle.FLAT,
        borderRadiusDp = 20,
        themeMode = DesignThemeMode.SYSTEM,
        defaultTextScale = 1.10f,
        defaultLargeTouchTargets = false,
    )

    @Test
    fun userPreferencesOverrideDeploymentDefaults() {
        val resolved = config.resolve(
            ThemePreferences(
                themeModeOverride = DesignThemeMode.DARK,
                textScaleOverride = 1.25f,
                largeTouchTargetsOverride = true,
            )
        )

        assertEquals(DesignThemeMode.DARK, resolved.themeMode)
        assertEquals(1.25f, resolved.textScale)
        assertTrue(resolved.largeTouchTargets)
        assertEquals(AppCardStyle.OUTLINED, resolved.cardStyle)
        assertEquals(AppButtonStyle.FLAT, resolved.buttonStyle)
        assertEquals(20, resolved.borderRadiusDp)
    }

    @Test
    fun deploymentDefaultsRemainWhenNoUserOverrideExists() {
        val resolved = config.resolve(ThemePreferences())

        assertEquals(DesignThemeMode.SYSTEM, resolved.themeMode)
        assertEquals(1.10f, resolved.textScale)
        assertFalse(resolved.largeTouchTargets)
    }

    @Test
    fun styleParsingIsCaseInsensitiveAndSafe() {
        assertEquals(AppCardStyle.OUTLINED, AppCardStyle.parse("outlined"))
        assertEquals(AppCardStyle.DEFAULT, AppCardStyle.parse("unknown"))
        assertEquals(AppButtonStyle.ELEVATED, AppButtonStyle.parse("ELEVATED"))
        assertEquals(AppButtonStyle.ELEVATED, AppButtonStyle.parse(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTextScaleIsRejected() {
        ThemePreferences(textScaleOverride = 2.0f)
    }
}
