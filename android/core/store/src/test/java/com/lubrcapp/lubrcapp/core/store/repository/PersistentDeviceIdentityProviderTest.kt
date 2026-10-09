package com.lubrcapp.lubrcapp.core.store.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lubrcapp.lubrcapp.domain.common.IdGenerator
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentDeviceIdentityProviderTest {

    @Test
    fun generatedDeviceIdIsReusedAcrossProviderInstances() = runTest {
        val directory = Files.createTempDirectory("device-identity-test").toFile()
        val file = File(directory, "device_identity.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )

        val first = PersistentDeviceIdentityProvider(
            dataStore = dataStore,
            idGenerator = FixedIdGenerator("device-first"),
        ).getOrCreate()

        val second = PersistentDeviceIdentityProvider(
            dataStore = dataStore,
            idGenerator = FixedIdGenerator("device-second"),
        ).getOrCreate()

        assertEquals("device-first", first.value)
        assertEquals(first, second)

        directory.deleteRecursively()
    }

    private class FixedIdGenerator(
        private val value: String,
    ) : IdGenerator {
        override fun generate(): String = value
    }
}
