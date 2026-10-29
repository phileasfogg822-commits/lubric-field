package com.lubrcapp.lubrcapp.shell

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.BusinessLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.SettingsLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.repository.UserLocalDataSource
import com.lubrcapp.lubrcapp.core.store.repository.RoomVpsBusinessRepository
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Phase 32.3 — owns demo-company seeding and deletion for the shell.
 * Seeding runs once per install (a DataStore flag), only when the
 * dashboard already has a current business to take the user id from;
 * deleting wipes exactly the demo scopes and never clears the flag,
 * so deleted demos never come back on their own.
 */
@HiltViewModel
class DemoCompaniesViewModel @Inject constructor(
    private val businessLocal: BusinessLocalDataSource,
    private val userLocal: UserLocalDataSource,
    private val settingsLocal: SettingsLocalDataSource,
    private val demoStore: RoomVpsBusinessRepository,
    private val deviceIdentity: DeviceIdentityProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val dataStore = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("demo_companies")
    }

    private val seededKey = booleanPreferencesKey("demo_seeded")

    suspend fun isSeeded(): Boolean =
        dataStore.data.map { it[seededKey] == true }.first()

    /**
     * Seeds every demo company for [userId]. Returns false (and
     * leaves the flag unset, so a later visit retries) when any
     * step fails; the seeder's deterministic ids make a retry
     * converge instead of duplicating.
     */
    suspend fun seedIfNeeded(userId: UserId, viewModels: DemoViewModels): Boolean {
        if (isSeeded()) return true
        return try {
            val deviceId = DeviceId(deviceIdentity.getOrCreate())
            DEMO_COMPANIES.forEach { def ->
                DemoSeeder.seedCompany(
                    def = def,
                    userId = userId,
                    deviceId = deviceId,
                    businessLocal = businessLocal,
                    userLocal = userLocal,
                    settingsLocal = settingsLocal,
                    viewModels = viewModels,
                )
            }
            dataStore.edit { it[seededKey] = true }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Wipes exactly the given (demo) scopes; the guard inside the store refuses anything else. */
    suspend fun deleteDemoCompanies(contexts: List<TenantContext>) {
        contexts.forEach { demoStore.deleteBusinessScope(it.tenantId.value, it.businessId.value) }
    }
}
