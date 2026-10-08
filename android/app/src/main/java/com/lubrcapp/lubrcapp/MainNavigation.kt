package com.lubrcapp.lubrcapp

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.lubrcapp.lubrcapp.feature.auth.AuthOnboardingMode

/**
 * App-level navigation host. Lives in the app module (not core:ui) so that
 * core:* never depends on feature:* - the app composes features together.
 *
 * The runtime theme is applied once by MainActivity.
 */
@Composable
fun MainNavigation(
    onNavigateToAuth: (AuthOnboardingMode) -> Unit
) {
    Scaffold(
        bottomBar = {
            // Bottom navigation will be populated based on navigation state
        }
    ) { paddingValues ->
        // Main content area. The actual screen is provided via Navigation composable or state.
    }
}
