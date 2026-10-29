package com.lubrcapp.lubrcapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.lubrcapp.lubrcapp.config.toDesignSystemConfig
import com.lubrcapp.lubrcapp.core.auth.AuthSessionController
import com.lubrcapp.lubrcapp.core.auth.DeviceSecurityPolicy
import com.lubrcapp.lubrcapp.core.auth.SecurityPolicyStore
import com.lubrcapp.lubrcapp.core.designsystem.AppSurface
import com.lubrcapp.lubrcapp.core.designsystem.LubrcappTheme
import com.lubrcapp.lubrcapp.core.designsystem.ThemePreferenceStore
import com.lubrcapp.lubrcapp.core.designsystem.ThemePreferences
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.auth.AuthViewModel
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.items.ItemsViewModel
import com.lubrcapp.lubrcapp.feature.parties.PartiesViewModel
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesViewModel
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import com.lubrcapp.lubrcapp.shell.DemoCompaniesViewModel
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfigSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var deploymentConfigSource: DeploymentConfigSource

    @Inject
    lateinit var themePreferenceStore: ThemePreferenceStore

    @Inject
    lateinit var securityPolicyStore: SecurityPolicyStore

    @Inject
    lateinit var sessionController: AuthSessionController

    private var latestSecurityPolicy = DeviceSecurityPolicy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            securityPolicyStore.initialize()
        }

        setContent {
            // Constructed at app entry so persisted auth/session state is
            // initialized even while the first local business is being created.
            hiltViewModel<AuthViewModel>()

            val deployment = remember { deploymentConfigSource.get() }
            val deploymentTheme = remember(deployment) {
                deployment.toDesignSystemConfig()
            }
            val preferences by themePreferenceStore.preferences.collectAsState(
                initial = ThemePreferences()
            )
            val securityPolicy by securityPolicyStore.policy.collectAsState()

            SideEffect {
                latestSecurityPolicy = securityPolicy
                if (securityPolicy.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LubrcappTheme(
                config = deploymentTheme,
                preferences = preferences,
            ) {
                AppSurface(modifier = Modifier.fillMaxSize()) {
                    MainNavigation(
                        appName = deployment.appName,
                        demoCompaniesViewModel = hiltViewModel<DemoCompaniesViewModel>(),
                        partiesViewModel = hiltViewModel<PartiesViewModel>(),
                        itemsViewModel = hiltViewModel<ItemsViewModel>(),
                        inventoryViewModel = hiltViewModel<InventoryViewModel>(),
                        salesViewModel = hiltViewModel<SalesViewModel>(),
                        purchasesViewModel = hiltViewModel<PurchasesViewModel>(),
                        accountsViewModel = hiltViewModel<AccountsViewModel>(),
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (latestSecurityPolicy.lockOnBackground) {
            lifecycleScope.launch {
                sessionController.lock()
            }
        }
    }
}
