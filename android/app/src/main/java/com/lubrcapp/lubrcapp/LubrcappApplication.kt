package com.lubrcapp.lubrcapp

import android.app.Application
import com.lubrcapp.lubrcapp.core.store.repository.DevicePairingManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LubrcappApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Re-apply the persisted cloud-sync pairing (if any) so scheduled
        // sync matches the stored credential after process restarts and
        // app updates. No pairing record means no scheduled work.
        applicationScope.launch {
            runCatching { DevicePairingManager(this@LubrcappApplication).restorePersisted() }
        }
    }
}
