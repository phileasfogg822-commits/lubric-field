package com.lubrcapp.lubrcapp.core.designsystem

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface ThemePreferenceStore {
    val preferences: Flow<ThemePreferences>

    suspend fun setThemeModeOverride(mode: DesignThemeMode?)
    suspend fun setTextScaleOverride(scale: Float?)
    suspend fun setLargeTouchTargetsOverride(enabled: Boolean?)
    suspend fun reset()
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ThemePreferencesDataStore

@Singleton
class DataStoreThemePreferenceStore @Inject constructor(
    @ThemePreferencesDataStore private val dataStore: DataStore<Preferences>,
) : ThemePreferenceStore {

    override val preferences: Flow<ThemePreferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { values ->
                ThemePreferences(
                    themeModeOverride = values[KEY_THEME_MODE]
                        ?.let { raw ->
                            DesignThemeMode.entries.firstOrNull {
                                it.name.equals(raw, ignoreCase = true)
                            }
                        },
                    textScaleOverride = values[KEY_TEXT_SCALE]
                        ?.takeIf { it in MIN_TEXT_SCALE..MAX_TEXT_SCALE },
                    largeTouchTargetsOverride = values[KEY_LARGE_TOUCH],
                )
            }

    override suspend fun setThemeModeOverride(mode: DesignThemeMode?) {
        dataStore.edit { values ->
            if (mode == null) values.remove(KEY_THEME_MODE)
            else values[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun setTextScaleOverride(scale: Float?) {
        require(scale == null || scale in MIN_TEXT_SCALE..MAX_TEXT_SCALE) {
            "Theme text scale must be between $MIN_TEXT_SCALE and $MAX_TEXT_SCALE"
        }
        dataStore.edit { values ->
            if (scale == null) values.remove(KEY_TEXT_SCALE)
            else values[KEY_TEXT_SCALE] = scale
        }
    }

    override suspend fun setLargeTouchTargetsOverride(enabled: Boolean?) {
        dataStore.edit { values ->
            if (enabled == null) values.remove(KEY_LARGE_TOUCH)
            else values[KEY_LARGE_TOUCH] = enabled
        }
    }

    override suspend fun reset() {
        dataStore.edit { values ->
            values.remove(KEY_THEME_MODE)
            values.remove(KEY_TEXT_SCALE)
            values.remove(KEY_LARGE_TOUCH)
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode_override")
        val KEY_TEXT_SCALE = floatPreferencesKey("text_scale_override")
        val KEY_LARGE_TOUCH = booleanPreferencesKey("large_touch_targets_override")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ThemePreferencesModule {
    private const val FILE_NAME = "design_theme_preferences"

    @Provides
    @Singleton
    @ThemePreferencesDataStore
    fun provideThemePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(FILE_NAME) }
        )

    @Provides
    @Singleton
    fun provideThemePreferenceStore(
        @ThemePreferencesDataStore dataStore: DataStore<Preferences>,
    ): ThemePreferenceStore = DataStoreThemePreferenceStore(dataStore)
}
