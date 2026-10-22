package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import com.lubrcapp.lubrcapp.core.repository.CloudPairingGateway
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.sync.SecureDeviceCredentials
import com.lubrcapp.lubrcapp.core.sync.StorageMode
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the three pieces of a cloud-sync pairing: the Keystore-backed
 * device credential, the persisted pairing record (endpoint + identity), and
 * the WorkManager schedule. A device holds one pairing at a time; pairing a
 * different scope first cancels the previous scope's scheduled work.
 */
@Singleton
class DevicePairingManager @Inject constructor(
    @ApplicationContext context: Context,
) : CloudPairingGateway {
    private val appContext = context.applicationContext
    private val credentials = SecureDeviceCredentials(appContext)
    private val scheduler = CloudSyncScheduler(appContext)
    private val preferences =
        appContext.getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE)

    internal data class PairingRecord(
        val identity: SyncIdentity,
        val endpoint: String,
        val tables: Set<String>,
    )

    internal fun record(): PairingRecord? {
        val endpoint = preferences.getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val tenant = preferences.getString(KEY_TENANT, null) ?: return null
        val business = preferences.getString(KEY_BUSINESS, null) ?: return null
        val device = preferences.getString(KEY_DEVICE, null) ?: return null
        val tables = preferences.getStringSet(KEY_TABLES, null)?.takeIf { it.isNotEmpty() }
            ?: RoomSyncQueueStore.SUPPORTED_TABLES
        return PairingRecord(SyncIdentity(tenant, business, device), endpoint, tables)
    }

    override suspend fun isPaired(scope: OperationalScope): Boolean =
        record()?.identity == scope.toSyncIdentity() && !credentials.token().isNullOrBlank()

    override fun pairedEndpoint(scope: OperationalScope): String? =
        record()?.takeIf { it.identity == scope.toSyncIdentity() }?.endpoint

    override suspend fun pair(scope: OperationalScope, endpoint: String, token: String) {
        require(endpoint.startsWith("https://")) { "Cloud sync endpoint must use HTTPS" }
        val identity = scope.toSyncIdentity()
        val previous = record()
        // Validates non-blank and encrypts into the Keystore-backed store;
        // throws before any pairing state changes on invalid input.
        credentials.saveToken(token)
        check(
            preferences.edit()
                .putString(KEY_ENDPOINT, endpoint)
                .putString(KEY_TENANT, identity.tenantId)
                .putString(KEY_BUSINESS, identity.businessId)
                .putString(KEY_DEVICE, identity.deviceId)
                .putStringSet(KEY_TABLES, RoomSyncQueueStore.SUPPORTED_TABLES)
                .commit()
        ) { "Unable to persist pairing record" }
        if (previous != null && previous.identity != identity) {
            scheduler.configure(
                previous.identity, StorageMode.LOCAL_ONLY, previous.endpoint, previous.tables
            )
        }
        scheduler.configure(
            identity, StorageMode.CLOUD_SYNC, endpoint, RoomSyncQueueStore.SUPPORTED_TABLES
        )
    }

    override suspend fun unpair(scope: OperationalScope) {
        val existing = record()
        val target = existing?.identity ?: scope.toSyncIdentity()
        credentials.clearPairing()
        preferences.edit().clear().apply()
        scheduler.configure(
            target, StorageMode.LOCAL_ONLY, existing?.endpoint ?: "",
            existing?.tables ?: RoomSyncQueueStore.SUPPORTED_TABLES,
        )
    }

    /**
     * Re-applies the persisted pairing after process start: re-schedules
     * cloud sync when a credential is still stored, otherwise ensures the
     * persisted identity is in LOCAL_ONLY so no stale work remains.
     */
    suspend fun restorePersisted() {
        val existing = record() ?: return
        if (credentials.token().isNullOrBlank()) {
            scheduler.configure(
                existing.identity, StorageMode.LOCAL_ONLY, existing.endpoint, existing.tables
            )
        } else {
            scheduler.configure(
                existing.identity, StorageMode.CLOUD_SYNC, existing.endpoint, existing.tables
            )
        }
    }

    private fun OperationalScope.toSyncIdentity() = SyncIdentity(tenantId, businessId, deviceId)

    private companion object {
        const val PAIRING_PREFS = "lubric_cloud_pairing"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TENANT = "tenant"
        const val KEY_BUSINESS = "business"
        const val KEY_DEVICE = "device"
        const val KEY_TABLES = "tables"
    }
}
