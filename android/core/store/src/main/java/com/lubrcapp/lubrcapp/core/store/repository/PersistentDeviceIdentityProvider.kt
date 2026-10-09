package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.IdGenerator
import com.lubrcapp.lubrcapp.domain.common.UuidIdGenerator
import com.lubrcapp.lubrcapp.domain.common.deviceId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceIdentityDataStore

@Singleton
class PersistentDeviceIdentityProvider @Inject constructor(
    @DeviceIdentityDataStore private val dataStore: DataStore<Preferences>,
) : DeviceIdentityProvider {
    private val mutex = Mutex()

    override suspend fun getOrCreate(): DeviceId =
        mutex.withLock {
            dataStore.data.first()[KEY_DEVICE_ID]?.let(::DeviceId)
                ?: UuidIdGenerator.deviceId().also { generated ->
                    dataStore.edit { values ->
                        values[KEY_DEVICE_ID] = generated.value
                    }
                }
        }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DeviceIdentityModule {
    private const val FILE_NAME = "device_identity"

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator

    @Provides
    @Singleton
    @DeviceIdentityDataStore
    fun provideDeviceIdentityDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(FILE_NAME) }
        )
}
