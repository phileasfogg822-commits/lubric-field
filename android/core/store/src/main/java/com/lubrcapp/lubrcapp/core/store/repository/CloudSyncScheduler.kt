package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lubrcapp.lubrcapp.core.sync.AuthenticatedVpsTransport
import com.lubrcapp.lubrcapp.core.sync.SecureDeviceCredentials
import com.lubrcapp.lubrcapp.core.sync.StorageMode
import com.lubrcapp.lubrcapp.core.sync.SyncEngine
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import androidx.room.Room
import java.util.concurrent.TimeUnit

/**
 * Explicit opt-in sync scheduling. LOCAL_ONLY always cancels scheduled network work.
 * WorkManager uniquely names each tenant/business/device job to prevent overlap.
 */
class CloudSyncScheduler(private val context: Context) {
    private val manager get() = WorkManager.getInstance(context)
    private fun jobName(identity: SyncIdentity) =
        "lubric-vps-sync:${identity.tenantId}:${identity.businessId}:${identity.deviceId}"

    fun configure(identity: SyncIdentity, mode: StorageMode, endpoint: String, tables: Set<String>) {
        val name = jobName(identity)
        val preferences = context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
        val enabledKey = "enabled:${identity.tenantId}:${identity.businessId}:${identity.deviceId}"
        preferences.edit().putBoolean(enabledKey, mode == StorageMode.CLOUD_SYNC).commit()
        if (mode != StorageMode.CLOUD_SYNC) {
            manager.cancelUniqueWork(name)
            manager.cancelUniqueWork(name + ":once")
            return
        }
        require(endpoint.startsWith("https://"))
        require(tables.isNotEmpty())
        val input = Data.Builder()
            .putString("tenant", identity.tenantId)
            .putString("business", identity.businessId)
            .putString("device", identity.deviceId)
            .putString("endpoint", endpoint)
            .putStringArray("tables", tables.toTypedArray())
            .build()
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, periodic)
    }

    fun syncNow(identity: SyncIdentity, mode: StorageMode, endpoint: String, tables: Set<String>) {
        if (mode != StorageMode.CLOUD_SYNC) return
        if (!CloudSyncConsent.isEnabled(context, identity)) return
        require(endpoint.startsWith("https://") && tables.isNotEmpty())
        val input = Data.Builder()
            .putString("tenant", identity.tenantId)
            .putString("business", identity.businessId)
            .putString("device", identity.deviceId)
            .putString("endpoint", endpoint)
            .putStringArray("tables", tables.toTypedArray())
            .build()
        manager.enqueueUniqueWork(
            jobName(identity) + ":once",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }
}

/**
 * Each execution reconstructs its durable Room queue; no in-memory operations are lost.
 * A missing or invalid pairing token fails the run without discarding local records.
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tenant = inputData.getString("tenant") ?: return Result.failure()
        val business = inputData.getString("business") ?: return Result.failure()
        val device = inputData.getString("device") ?: return Result.failure()
        val endpoint = inputData.getString("endpoint") ?: return Result.failure()
        val tables = inputData.getStringArray("tables")?.toSet() ?: return Result.failure()
        val identity = SyncIdentity(tenant, business, device)
        if (!CloudSyncConsent.isEnabled(applicationContext, identity)) return Result.success()
        val credentials = SecureDeviceCredentials(applicationContext)
        if (credentials.token().isNullOrBlank()) return Result.failure()
        val database = Room.databaseBuilder(
            applicationContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME
        ).addMigrations(*com.lubrcapp.lubrcapp.core.store.db.DatabaseMigrations.ALL)
            .addCallback(com.lubrcapp.lubrcapp.core.store.db.AuditIntegrityGuard.callback)
            .build()
        return try {
            SyncEngine(
                RoomSyncQueueStore(database),
                AuthenticatedVpsTransport(endpoint, credentials, tables) { CloudSyncConsent.isEnabled(applicationContext, identity) }
            ).run(if (CloudSyncConsent.isEnabled(applicationContext, identity)) StorageMode.CLOUD_SYNC else StorageMode.LOCAL_ONLY, identity)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            database.close()
        }
    }
}

internal object CloudSyncConsent {
    fun isEnabled(context: Context, identity: SyncIdentity): Boolean =
        context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
            .getBoolean("enabled:${identity.tenantId}:${identity.businessId}:${identity.deviceId}", false)
}
