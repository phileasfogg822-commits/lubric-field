package com.lubrcapp.lubrcapp.config

import com.lubrcapp.lubrcapp.core.designsystem.AppButtonStyle
import com.lubrcapp.lubrcapp.core.designsystem.AppCardStyle
import com.lubrcapp.lubrcapp.core.designsystem.BrandPalette
import com.lubrcapp.lubrcapp.core.designsystem.DesignSystemConfig
import com.lubrcapp.lubrcapp.core.designsystem.DesignThemeMode
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingConfig
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfig
import com.lubrcapp.lubrcapp.whitelist.config.ResolvedWhiteLabelConfig
import com.lubrcapp.lubrcapp.whitelist.config.ThemeMode

fun DeploymentConfig.toDesignSystemConfig(): DesignSystemConfig =
    branding.toDesignSystemConfig(
        themeMode = defaultThemeMode,
        defaultTextScale = defaultTextScale,
        defaultLargeTouchTargets = defaultLargeTouchTargets,
    )

fun ResolvedWhiteLabelConfig.toDesignSystemConfig(): DesignSystemConfig =
    branding.toDesignSystemConfig(
        themeMode = themeMode,
        defaultTextScale = deployment.defaultTextScale,
        defaultLargeTouchTargets = deployment.defaultLargeTouchTargets,
    )

private fun BrandingConfig.toDesignSystemConfig(
    themeMode: ThemeMode,
    defaultTextScale: Float,
    defaultLargeTouchTargets: Boolean,
): DesignSystemConfig =
    DesignSystemConfig(
        palette = BrandPalette(
            primary = primaryColor,
            secondary = secondaryColor,
            accent = accentColor,
            success = successColor,
            warning = warningColor,
            error = errorColor,
        ),
        fontFamilyName = fontFamily,
        cardStyle = AppCardStyle.parse(cardStyle),
        buttonStyle = AppButtonStyle.parse(buttonStyle),
        borderRadiusDp = borderRadius,
        themeMode = themeMode.toDesignThemeMode(),
        defaultTextScale = defaultTextScale,
        defaultLargeTouchTargets = defaultLargeTouchTargets,
    )

private fun ThemeMode.toDesignThemeMode(): DesignThemeMode =
    when (this) {
        ThemeMode.LIGHT -> DesignThemeMode.LIGHT
        ThemeMode.DARK -> DesignThemeMode.DARK
        ThemeMode.SYSTEM -> DesignThemeMode.SYSTEM
    }
