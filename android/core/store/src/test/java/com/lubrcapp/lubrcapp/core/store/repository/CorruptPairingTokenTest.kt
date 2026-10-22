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
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 12C-06 residual: corrupt stored credential behavior at the layers that
 * are testable without a real Android Keystore.
 *
 * SecureDeviceCredentials.token() fails safe to null on any storage or
 * decryption problem (missing key, invalidated key, corrupt ciphertext).
 * That single contract drives everything asserted here: a present-but-
 * corrupt token must behave exactly like no token — the device reports
 * unpaired, restore keeps the scope LOCAL_ONLY, and the sync worker fails
 * its run without touching business data. (The Keystore crypto round-trip
 * itself needs a device; under Robolectric the AndroidKeyStore provider
 * is absent, which lands in the same fail-safe path production takes on
 * corruption, so the observable contract is what is pinned.)
 *
 * There is no client-side token expiry in this design: an expired token
 * is indistinguishable from a valid one until the VPS rejects it, which
 * surfaces as the transport's HTTP 401 error — covered in core:sync by
 * TransportTokenRedactionTest.expiredTokenFailsWithRedactedError.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorruptPairingTokenTest {
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

    /** A valid pairing record whose stored credential bytes are garbage. */
    private fun seedRecordWithCorruptCredential() {
        context.getSharedPreferences("lubric_cloud_pairing", Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", "https://vps.example")
            .putString("tenant", "tenant-a")
            .putString("business", "business-a")
            .putString("device", "device-a")
            .commit()
        context.getSharedPreferences("lubric_secure_pairing", Context.MODE_PRIVATE)
            .edit()
            .putString("iv", "!!!not-valid-base64!!!")
            .putString("token", "corrupt-ciphertext-not-produced-by-keystore")
            .commit()
    }

    @Test fun corruptStoredTokenFailsSafeToUnpairedAndRestoreKeepsLocalOnly() = runBlocking {
        seedRecordWithCorruptCredential()
        // The pairing RECORD is intact; only the credential is unusable.
        assertNotNull(manager.record())
        // Fail-safe: corrupt token reads as no token, so the scope is not
        // paired even though its record matches exactly.
        assertFalse(manager.isPaired(scope))
        // Startup restore must therefore keep the scope in LOCAL_ONLY:
        // cloud consent stays disabled and no cloud job is enqueued.
        manager.restorePersisted()
        assertFalse(CloudSyncConsent.isEnabled(context, identity))
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(jobName).get()
        assertTrue(infos.none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test fun workerFailsRunWhenStoredTokenIsCorrupt() = runBlocking {
        seedRecordWithCorruptCredential()
        // Consent is ON for this identity, so the worker proceeds past the
        // consent gate and reaches the credential check.
        context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled:tenant-a:business-a:device-a", true)
            .commit()
        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString("tenant", identity.tenantId)
                    .putString("business", identity.businessId)
                    .putString("device", identity.deviceId)
                    .putString("endpoint", "https://vps.example")
                    .putStringArray("tables", arrayOf("products"))
                    .build()
            )
            .build()
        // A corrupt token fails the run cleanly (WorkManager will retry
        // per backoff after re-pairing); it must not report success and
        // must not fall through to a database or network attempt with a
        // garbage credential.
        assertTrue(worker.doWork() is ListenableWorker.Result.Failure)
    }
}
