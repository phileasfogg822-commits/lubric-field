package com.lubrcapp.lubrcapp.core.designsystem

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePreferenceStoreTest {

    @Test
    fun preferencesPersistAndCanBeReset() = runTest {
        val directory = Files.createTempDirectory("theme-preferences-test").toFile()
        val file = File(directory, "theme.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val store = DataStoreThemePreferenceStore(dataStore)

        store.setThemeModeOverride(DesignThemeMode.DARK)
        store.setTextScaleOverride(1.25f)
        store.setLargeTouchTargetsOverride(true)

        val persisted = store.preferences.first()
        assertEquals(DesignThemeMode.DARK, persisted.themeModeOverride)
        assertEquals(1.25f, persisted.textScaleOverride)
        assertEquals(true, persisted.largeTouchTargetsOverride)

        store.reset()

        val reset = store.preferences.first()
        assertNull(reset.themeModeOverride)
        assertNull(reset.textScaleOverride)
        assertNull(reset.largeTouchTargetsOverride)

        directory.deleteRecursively()
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTextScaleCannotBePersisted() = runTest {
        val directory = Files.createTempDirectory("theme-preferences-invalid").toFile()
        val file = File(directory, "theme.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val store = DataStoreThemePreferenceStore(dataStore)

        store.setTextScaleOverride(2.0f)
    }
}
