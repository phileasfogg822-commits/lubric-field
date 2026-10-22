package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.sync.StorageMode
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pairing lifecycle verification for Phase 12.3.3/12.3.4. The Keystore
 * crypto round-trip itself needs a real device keystore, so these tests
 * cover validation, the persisted pairing record, scheduling side effects
 * and the restore path — the states that decide whether sync can start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DevicePairingManagerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")
    private val jobName = "lubric-vps-sync:tenant-a:business-a:device-a"
    private val manager get() = DevicePairingManager(context)

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().build()
        )
        context.getSharedPreferences("lubric_cloud_pairing", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("lubric_secure_pairing", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun seedPairingRecord() {
        context.getSharedPreferences("lubric_cloud_pairing", Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", "https://vps.example")
            .putString("tenant", "tenant-a")
            .putString("business", "business-a")
            .putString("device", "device-a")
            .commit()
    }

    private fun workInfos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(jobName).get()

    @Test fun pairRejectsHttpEndpointBeforeStoringAnything() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.pair(scope, "http://vps.example", "token") }
        }
        assertNull(manager.record())
    }

    @Test fun pairRejectsBlankToken() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.pair(scope, "https://vps.example", "  ") }
        }
        assertNull(manager.record())
    }

    @Test fun unpairClearsRecordAndCancelsScheduledSync() = runBlocking {
        seedPairingRecord()
        CloudSyncScheduler(context).configure(
            identity, StorageMode.CLOUD_SYNC, "https://vps.example", setOf("products")
        )
        assertTrue(workInfos().any { it.state == WorkInfo.State.ENQUEUED })

        manager.unpair(scope)

        assertNull(manager.record())
        assertTrue(workInfos().all { it.state == WorkInfo.State.CANCELLED })
        assertFalse(CloudSyncConsent.isEnabled(context, identity))
        assertFalse(manager.isPaired(scope))
        assertEquals(null, manager.pairedEndpoint(scope))
    }

    @Test fun restoreWithoutStoredTokenKeepsLocalOnly() = runBlocking {
        seedPairingRecord()
        manager.restorePersisted()
        assertFalse(CloudSyncConsent.isEnabled(context, identity))
        assertTrue(workInfos().none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test fun pairedEndpointRequiresMatchingScope() {
        seedPairingRecord()
        assertEquals("https://vps.example", manager.pairedEndpoint(scope))
        assertEquals(
            null,
            manager.pairedEndpoint(OperationalScope("tenant-a", "business-b", "device-a")),
        )
    }
}
