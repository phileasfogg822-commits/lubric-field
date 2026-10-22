package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.sync.SyncIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudSyncConsentTest {
    private val identity = SyncIdentity("tenant-a", "business-a", "device-a")
    private lateinit var context: Context

    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun localOnlyIsDefaultAndOptOutDisablesAccess() {
        assertFalse(CloudSyncConsent.isEnabled(context, identity))
        val preferences = context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("enabled:tenant-a:business-a:device-a", true).commit()
        assertTrue(CloudSyncConsent.isEnabled(context, identity))
        preferences.edit().putBoolean("enabled:tenant-a:business-a:device-a", false).commit()
        assertFalse(CloudSyncConsent.isEnabled(context, identity))
    }

    @Test fun cloudConsentNeverLeaksAcrossBusinessOrDevice() {
        context.getSharedPreferences("lubric_cloud_sync", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled:tenant-a:business-a:device-a", true).commit()
        assertFalse(CloudSyncConsent.isEnabled(context, SyncIdentity("tenant-a", "business-b", "device-a")))
        assertFalse(CloudSyncConsent.isEnabled(context, SyncIdentity("tenant-a", "business-a", "device-b")))
        assertFalse(CloudSyncConsent.isEnabled(context, SyncIdentity("tenant-b", "business-a", "device-a")))
    }
}
