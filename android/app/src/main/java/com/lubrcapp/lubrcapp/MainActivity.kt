package com.lubrcapp.lubrcapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.config.toDesignSystemConfig
import com.lubrcapp.lubrcapp.core.designsystem.AppSurface
import com.lubrcapp.lubrcapp.core.designsystem.LubrcappTheme
import com.lubrcapp.lubrcapp.core.designsystem.ThemePreferenceStore
import com.lubrcapp.lubrcapp.core.designsystem.ThemePreferences
import com.lubrcapp.lubrcapp.feature.auth.AuthViewModel
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfigSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deploymentConfigSource: DeploymentConfigSource

    @Inject
    lateinit var themePreferenceStore: ThemePreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            val deploymentTheme = remember {
                deploymentConfigSource.get().toDesignSystemConfig()
            }
            val preferences by themePreferenceStore.preferences.collectAsState(
                initial = ThemePreferences()
            )

            LubrcappTheme(
                config = deploymentTheme,
                preferences = preferences,
            ) {
                AppSurface(modifier = Modifier.fillMaxSize()) {
                    MainNavigation(
                        onNavigateToAuth = authViewModel::showOnboarding
                    )
                }
            }
        }
    }
}
