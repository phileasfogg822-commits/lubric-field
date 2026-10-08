package com.lubrcapp.lubrcapp.config

import com.lubrcapp.lubrcapp.BuildConfig
import com.lubrcapp.lubrcapp.core.network.ApiEndpointProvider
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingConfig
import com.lubrcapp.lubrcapp.whitelist.config.ConfigurationCodecs
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfig
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfigSource
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentEnvironment
import com.lubrcapp.lubrcapp.whitelist.config.TaxConfig
import com.lubrcapp.lubrcapp.whitelist.config.TerminologyConfig
import com.lubrcapp.lubrcapp.whitelist.config.ThemeMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppDeploymentConfigModule {

    @Provides
    @Singleton
    fun provideDeploymentConfigSource(): DeploymentConfigSource =
        DeploymentConfigSource {
            DeploymentConfig(
                appName = BuildConfig.DEPLOYMENT_APP_NAME,
                applicationId = BuildConfig.APPLICATION_ID,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                apiEndpoint = BuildConfig.DEPLOYMENT_API_ENDPOINT,
                environment = DeploymentEnvironment.parse(BuildConfig.DEPLOYMENT_ENVIRONMENT),
                firebaseProjectId = ConfigurationCodecs.optional(
                    BuildConfig.DEPLOYMENT_FIREBASE_PROJECT_ID
                ),
                licenseId = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_LICENSE_ID),
                includedModules = ConfigurationCodecs.csvSet(
                    BuildConfig.DEPLOYMENT_INCLUDED_MODULES
                ),
                branding = BrandingConfig(
                    logoUri = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_LOGO_URI),
                    primaryColor = requireColor(BuildConfig.DEPLOYMENT_PRIMARY_COLOR),
                    secondaryColor = requireColor(BuildConfig.DEPLOYMENT_SECONDARY_COLOR),
                    accentColor = requireColor(BuildConfig.DEPLOYMENT_ACCENT_COLOR),
                    successColor = requireColor(BuildConfig.DEPLOYMENT_SUCCESS_COLOR),
                    warningColor = requireColor(BuildConfig.DEPLOYMENT_WARNING_COLOR),
                    errorColor = requireColor(BuildConfig.DEPLOYMENT_ERROR_COLOR),
                    fontFamily = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_FONT_FAMILY),
                    cardStyle = BuildConfig.DEPLOYMENT_CARD_STYLE,
                    buttonStyle = BuildConfig.DEPLOYMENT_BUTTON_STYLE,
                    borderRadius = BuildConfig.DEPLOYMENT_BORDER_RADIUS,
                ),
                terminology = TerminologyConfig(
                    sale = BuildConfig.TERM_SALE,
                    invoice = BuildConfig.TERM_INVOICE,
                    taxInvoice = BuildConfig.TERM_TAX_INVOICE,
                    cashMemo = BuildConfig.TERM_CASH_MEMO,
                    quotation = BuildConfig.TERM_QUOTATION,
                    estimate = BuildConfig.TERM_ESTIMATE,
                    customer = BuildConfig.TERM_CUSTOMER,
                    supplier = BuildConfig.TERM_SUPPLIER,
                    party = BuildConfig.TERM_PARTY,
                    item = BuildConfig.TERM_ITEM,
                    category = BuildConfig.TERM_CATEGORY,
                    brand = BuildConfig.TERM_BRAND,
                    unit = BuildConfig.TERM_UNIT,
                    warehouse = BuildConfig.TERM_WAREHOUSE,
                    paymentMode = BuildConfig.TERM_PAYMENT_MODE,
                    expenseCategory = BuildConfig.TERM_EXPENSE_CATEGORY,
                    otherIncome = BuildConfig.TERM_OTHER_INCOME,
                ),
                defaultTax = TaxConfig(
                    enabled = BuildConfig.DEFAULT_TAX_ENABLED,
                    label = BuildConfig.DEFAULT_TAX_LABEL,
                    numberLabel = BuildConfig.DEFAULT_TAX_NUMBER_LABEL,
                    defaultRate = ConfigurationCodecs.percentage(BuildConfig.DEFAULT_TAX_RATE)
                        ?: Percentage.ZERO,
                ),
                defaultPaymentMethods = requireNotNull(
                    ConfigurationCodecs.paymentMethods(BuildConfig.DEFAULT_PAYMENT_METHODS)
                ) { "Deployment paymentMethods must contain at least one valid method" },
                defaultLanguage = BuildConfig.DEFAULT_LANGUAGE,
                defaultThemeMode = requireNotNull(
                    ThemeMode.parseOrNull(BuildConfig.DEFAULT_THEME_MODE)
                ) { "Invalid deployment defaultThemeMode" },
                supportEmail = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_SUPPORT_EMAIL),
                supportPhone = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_SUPPORT_PHONE),
                termsUrl = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_TERMS_URL),
                privacyUrl = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_PRIVACY_URL),
                website = ConfigurationCodecs.optional(BuildConfig.DEPLOYMENT_WEBSITE),
                defaultTextScale = BuildConfig.DEFAULT_TEXT_SCALE.toFloat(),
                defaultLargeTouchTargets = BuildConfig.DEFAULT_LARGE_TOUCH_TARGETS,
            )
        }

    @Provides
    @Singleton
    fun provideApiEndpointProvider(
        deploymentSource: DeploymentConfigSource,
    ): ApiEndpointProvider =
        ApiEndpointProvider { deploymentSource.get().apiEndpoint }

    private fun requireColor(raw: String): Int =
        requireNotNull(ConfigurationCodecs.argb(raw)) {
            "Invalid ARGB deployment color: $raw"
        }
}
