package com.lubrcapp.lubrcapp.whitelist.featureflags

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.whitelist.config.WhiteLabelConfigService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FeatureFlagService {
    fun observeEnabledFeatures(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<Set<String>>

    suspend fun isEnabled(
        tenantId: TenantId,
        businessId: BusinessId,
        feature: String,
    ): Boolean

    fun getAllFeatureKeys(): Set<String>
}

@Singleton
class ResolvedFeatureFlagService @Inject constructor(
    private val whiteLabelConfigService: WhiteLabelConfigService,
) : FeatureFlagService {
    override fun observeEnabledFeatures(
        tenantId: TenantId,
        businessId: BusinessId,
    ): Flow<Set<String>> =
        whiteLabelConfigService.observeResolvedConfig(tenantId, businessId)
            .map { it.enabledModules }

    override suspend fun isEnabled(
        tenantId: TenantId,
        businessId: BusinessId,
        feature: String,
    ): Boolean =
        whiteLabelConfigService.getResolvedConfig(tenantId, businessId)
            .isModuleEnabled(feature)

    override fun getAllFeatureKeys(): Set<String> = FeatureFlagKeys.ALL
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureFlagModule {
    @Binds
    @Singleton
    abstract fun bindFeatureFlagService(
        implementation: ResolvedFeatureFlagService,
    ): FeatureFlagService
}

object FeatureFlagKeys {
    const val MODULE_INVENTORY = "inventory"
    const val MODULE_ORDERS = "orders"
    const val MODULE_PURCHASES = "purchases"
    const val MODULE_PAYS = "pays"
    const val MODULE_ACCOUNTING = "accounting"
    const val MODULE_REPORTS = "reports"
    const val MODULE_USERS = "users"
    const val MODULE_SYNC = "sync"
    const val MODULE_BACKUP = "backup"
    const val MODULE_NOTIFICATIONS = "notifications"
    const val MODULE_PRINTING = "printing"
    const val MODULE_FIELD_SALES = "field_sales"
    const val MODULE_ONLINE_STORE = "online_store"
    const val MODULE_BATCH_TRACKING = "batch_tracking"
    const val MODULE_SERIAL_TRACKING = "serial_tracking"
    const val MODULE_MULTI_BRANCH = "multi_branch"
    const val MODULE_ROUTES = "routes"
    const val MODULE_DELIVERY = "delivery"
    const val MODULE_PAYMENT_REMINDERS = "payment_reminders"
    const val MODULE_BARCODES = "barcodes"
    const val MODULE_RETURNS = "returns"
    const val MODULE_EXPENSES = "expenses"
    const val MODULE_OTHER_INCOME = "other_income"
    const val MODULE_CASH_BANK = "cash_bank"
    const val MODULE_TAX = "tax"
    const val MODULE_TRANSLATION = "translation"

    val ALL: Set<String> = setOf(
        MODULE_INVENTORY,
        MODULE_ORDERS,
        MODULE_PURCHASES,
        MODULE_PAYS,
        MODULE_ACCOUNTING,
        MODULE_REPORTS,
        MODULE_USERS,
        MODULE_SYNC,
        MODULE_BACKUP,
        MODULE_NOTIFICATIONS,
        MODULE_PRINTING,
        MODULE_FIELD_SALES,
        MODULE_ONLINE_STORE,
        MODULE_BATCH_TRACKING,
        MODULE_SERIAL_TRACKING,
        MODULE_MULTI_BRANCH,
        MODULE_ROUTES,
        MODULE_DELIVERY,
        MODULE_PAYMENT_REMINDERS,
        MODULE_BARCODES,
        MODULE_RETURNS,
        MODULE_EXPENSES,
        MODULE_OTHER_INCOME,
        MODULE_CASH_BANK,
        MODULE_TAX,
        MODULE_TRANSLATION,
    )
}
