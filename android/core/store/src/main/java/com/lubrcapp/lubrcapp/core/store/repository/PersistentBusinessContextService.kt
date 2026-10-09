package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lubrcapp.lubrcapp.core.repository.BusinessChoice
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitcher
import com.lubrcapp.lubrcapp.core.repository.CurrentBusinessProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.store.db.dao.AccessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.BusinessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SettingsDao
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BusinessContextDataStore

@Singleton
class PersistentBusinessContextService @Inject constructor(
    private val businessDao: BusinessDao,
    private val accessDao: AccessDao,
    private val settingsDao: SettingsDao,
    @BusinessContextDataStore private val dataStore: DataStore<Preferences>,
) : CurrentBusinessProvider, BusinessSwitcher {

    private val mutationMutex = Mutex()

    override fun observeCurrent(
        userId: UserId,
    ): Flow<BusinessChoice?> =
        dataStore.data
            .mapLatest { preferences -> readContext(preferences, userId) }
            .distinctUntilChanged()
            .flatMapLatest { context ->
                if (context == null) {
                    flowOf(null)
                } else {
                    accessDao.observeAccessibleBusiness(
                        userId = context.userId.value,
                        tenantId = context.tenantId.value,
                        businessId = context.businessId.value,
                    ).mapLatest { business ->
                        business?.let { buildChoice(it, context.userId) }
                    }
                }
            }

    override suspend fun current(
        userId: UserId,
    ): BusinessChoice? =
        readContext(dataStore.data.first(), userId)
            ?.let { validateChoice(it) }

    override suspend fun ensureCurrent(
        userId: UserId,
        fallback: TenantContext?,
    ): BusinessChoice? =
        mutationMutex.withLock {
            val persisted = readContext(dataStore.data.first(), userId)
                ?.let { validateChoice(it) }
            if (persisted != null) return@withLock persisted

            val validatedFallback = fallback
                ?.takeIf { it.userId == userId }
                ?.let { validateChoice(it) }
            if (validatedFallback != null) {
                persist(validatedFallback.context)
                return@withLock validatedFallback
            }

            val firstAccessible = accessDao.getBusinessesForUser(userId.value)
                .firstOrNull()
                ?.let { buildChoice(it, userId) }

            if (firstAccessible != null) {
                persist(firstAccessible.context)
            } else {
                clearPersistedContext()
            }
            firstAccessible
        }

    override fun observeAvailableBusinesses(
        userId: UserId,
    ): Flow<List<BusinessChoice>> =
        accessDao.observeBusinessesForUser(userId.value)
            .mapLatest { businesses ->
                businesses.map { business ->
                    buildChoice(business, userId)
                }
            }

    override suspend fun switchBusiness(
        userId: UserId,
        tenantId: TenantId,
        businessId: BusinessId,
    ): BusinessSwitchResult =
        mutationMutex.withLock {
            val business = businessDao.getActiveById(
                tenantId.value,
                businessId.value,
            ) ?: return@withLock BusinessSwitchResult.Unavailable(
                tenantId = tenantId,
                businessId = businessId,
            )

            val membership = accessDao.getMembership(
                tenantId.value,
                businessId.value,
                userId.value,
            ) ?: return@withLock BusinessSwitchResult.AccessDenied(
                tenantId = tenantId,
                businessId = businessId,
            )

            if (!membership.isActive || membership.deletedAt != null) {
                return@withLock BusinessSwitchResult.AccessDenied(
                    tenantId = tenantId,
                    businessId = businessId,
                )
            }

            val choice = buildChoice(business, userId)
            persist(choice.context)
            BusinessSwitchResult.Switched(choice)
        }

    private suspend fun validateChoice(
        context: TenantContext,
    ): BusinessChoice? {
        val business = businessDao.getActiveById(
            context.tenantId.value,
            context.businessId.value,
        ) ?: return null

        val membership = accessDao.getMembership(
            context.tenantId.value,
            context.businessId.value,
            context.userId.value,
        ) ?: return null

        if (!membership.isActive || membership.deletedAt != null) {
            return null
        }

        return buildChoice(business, context.userId)
    }

    private suspend fun buildChoice(
        business: BusinessEntity,
        userId: UserId,
    ): BusinessChoice {
        val languageCode = settingsDao.get(
            business.tenantId,
            business.id,
            BusinessSetupSettingKeys.LOCALE_LANGUAGE,
        )?.value
            ?.trim()
            .orEmpty()

        return BusinessChoice(
            context = TenantContext(
                tenantId = TenantId(business.tenantId),
                businessId = BusinessId(business.id),
                userId = userId,
            ),
            name = business.name,
            businessType = business.businessType,
            currency = CurrencyCode.of(business.currencyCode),
            timezone = ZoneId.of(business.timezoneId),
            languageCode = languageCode,
        )
    }

    private fun readContext(
        preferences: Preferences,
        userId: UserId,
    ): TenantContext? {
        val persistedUserId = preferences[KEY_USER_ID] ?: return null
        if (persistedUserId != userId.value) return null

        val tenantId = preferences[KEY_TENANT_ID] ?: return null
        val businessId = preferences[KEY_BUSINESS_ID] ?: return null

        return TenantContext(
            tenantId = TenantId(tenantId),
            businessId = BusinessId(businessId),
            userId = userId,
        )
    }

    private suspend fun persist(
        context: TenantContext,
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = context.userId.value
            preferences[KEY_TENANT_ID] = context.tenantId.value
            preferences[KEY_BUSINESS_ID] = context.businessId.value
        }
    }

    private suspend fun clearPersistedContext() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_USER_ID)
            preferences.remove(KEY_TENANT_ID)
            preferences.remove(KEY_BUSINESS_ID)
        }
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("current_user_id")
        val KEY_TENANT_ID = stringPreferencesKey("current_tenant_id")
        val KEY_BUSINESS_ID = stringPreferencesKey("current_business_id")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BusinessContextDataStoreModule {
    private const val FILE_NAME = "business_context"

    @Provides
    @Singleton
    @BusinessContextDataStore
    fun provideBusinessContextDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(FILE_NAME) }
        )
}
