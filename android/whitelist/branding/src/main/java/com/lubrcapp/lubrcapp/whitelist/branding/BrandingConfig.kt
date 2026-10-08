package com.lubrcapp.lubrcapp.whitelist.branding

import androidx.annotation.ColorInt

/**
 * Fully resolved customer-facing brand values.
 *
 * Deployment defaults are supplied by the white-label deployment config.
 * Business-specific values are layered on top by the configuration engine.
 */
data class BrandingConfig(
    val companyName: String? = null,
    val logoUri: String? = null,
    @ColorInt val primaryColor: Int,
    @ColorInt val secondaryColor: Int,
    @ColorInt val accentColor: Int,
    @ColorInt val successColor: Int,
    @ColorInt val warningColor: Int,
    @ColorInt val errorColor: Int,
    val fontFamily: String? = null,
    val cardStyle: String,
    val buttonStyle: String,
    val borderRadius: Int,
) {
    init {
        require(borderRadius >= 0) { "borderRadius cannot be negative" }
    }
}

data class BrandingOverrides(
    val companyName: String? = null,
    val logoUri: String? = null,
    @ColorInt val primaryColor: Int? = null,
    @ColorInt val secondaryColor: Int? = null,
    @ColorInt val accentColor: Int? = null,
    @ColorInt val successColor: Int? = null,
    @ColorInt val warningColor: Int? = null,
    @ColorInt val errorColor: Int? = null,
    val fontFamily: String? = null,
    val cardStyle: String? = null,
    val buttonStyle: String? = null,
    val borderRadius: Int? = null,
)

fun BrandingConfig.applyOverrides(
    overrides: BrandingOverrides,
    businessNameFallback: String? = null,
): BrandingConfig = copy(
    companyName = overrides.companyName ?: businessNameFallback ?: companyName,
    logoUri = overrides.logoUri ?: logoUri,
    primaryColor = overrides.primaryColor ?: primaryColor,
    secondaryColor = overrides.secondaryColor ?: secondaryColor,
    accentColor = overrides.accentColor ?: accentColor,
    successColor = overrides.successColor ?: successColor,
    warningColor = overrides.warningColor ?: warningColor,
    errorColor = overrides.errorColor ?: errorColor,
    fontFamily = overrides.fontFamily ?: fontFamily,
    cardStyle = overrides.cardStyle ?: cardStyle,
    buttonStyle = overrides.buttonStyle ?: buttonStyle,
    borderRadius = overrides.borderRadius ?: borderRadius,
)
