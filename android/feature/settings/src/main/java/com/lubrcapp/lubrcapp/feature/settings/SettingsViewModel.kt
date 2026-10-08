package com.lubrcapp.lubrcapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lubrcapp.lubrcapp.core.designsystem.DesignThemeMode
import com.lubrcapp.lubrcapp.core.designsystem.ThemePreferenceStore
import com.lubrcapp.lubrcapp.core.designsystem.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferenceStore: ThemePreferenceStore,
) : ViewModel() {

    val themePreferences: Flow<ThemePreferences> = themePreferenceStore.preferences

    fun setThemeMode(mode: DesignThemeMode?) {
        viewModelScope.launch {
            themePreferenceStore.setThemeModeOverride(mode)
        }
    }

    fun setTextScale(scale: Float?) {
        viewModelScope.launch {
            themePreferenceStore.setTextScaleOverride(scale)
        }
    }

    fun setLargeTouchTargets(enabled: Boolean?) {
        viewModelScope.launch {
            themePreferenceStore.setLargeTouchTargetsOverride(enabled)
        }
    }

    fun resetThemePreferences() {
        viewModelScope.launch {
            themePreferenceStore.reset()
        }
    }
}
