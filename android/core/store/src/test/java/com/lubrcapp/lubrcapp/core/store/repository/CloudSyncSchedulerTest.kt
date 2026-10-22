package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.lubrcapp.lubrcapp.core.sync.StorageMode
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scheduler/worker boundary verification for Phase 12.4.6 / 12C-07:
 * LOCAL_ONLY must cancel scheduled cloud work, and the worker must do
 * no network or database work while cloud consent is disabled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudSyncSchedulerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")
    private val jobName = "lubric-vps-sync:tenant-a:business-a:device-a"

    @Before fun setUp() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun workInfos(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    @Test fun localOnlyCancelsScheduledCloudSync() {
        val scheduler = CloudSyncScheduler(context)
        scheduler.configure(identity, StorageMode.CLOUD_SYNC, "https://example.invalid", setOf("products"))
        assertTrue(workInfos(jobName).any { it.state == WorkInfo.State.ENQUEUED })
        scheduler.configure(identity, StorageMode.LOCAL_ONLY, "https://example.invalid", setOf("products"))
        assertTrue(workInfos(jobName).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test fun workerWithConsentDisabledDoesNoNetworkOrDatabaseWork() = runBlocking {
        // Cloud consent is opt-in and defaults to disabled. With consent off,
        // the worker must succeed immediately: the endpoint is unreachable and
        // no pairing token exists, so any network or database attempt would
        // fail this test instead.
        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString("tenant", identity.tenantId)
                    .putString("business", identity.businessId)
                    .putString("device", identity.deviceId)
                    .putString("endpoint", "https://example.invalid")
                    .putStringArray("tables", arrayOf("products"))
                    .build()
            )
            .build()
        assertTrue(worker.doWork() is ListenableWorker.Result.Success)
    }

    @Test fun cancellingOneIdentityLeavesAnotherIdentityScheduled() {
        val scheduler = CloudSyncScheduler(context)
        val other = SyncIdentity("tenant-a", "business-a", "device-b")
        val otherJob = "lubric-vps-sync:tenant-a:business-a:device-b"
        scheduler.configure(identity, StorageMode.CLOUD_SYNC, "https://example.invalid", setOf("products"))
        scheduler.configure(other, StorageMode.CLOUD_SYNC, "https://example.invalid", setOf("products"))
        assertTrue(workInfos(otherJob).any { it.state == WorkInfo.State.ENQUEUED })
        scheduler.configure(other, StorageMode.LOCAL_ONLY, "https://example.invalid", setOf("products"))
        assertTrue(workInfos(otherJob).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(workInfos(jobName).any { it.state == WorkInfo.State.ENQUEUED })
    }
}
