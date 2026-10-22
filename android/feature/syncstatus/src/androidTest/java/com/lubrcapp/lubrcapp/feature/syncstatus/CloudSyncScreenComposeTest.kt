package com.lubrcapp.lubrcapp.feature.syncstatus

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.repository.CloudPairingGateway
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeGateway : CloudPairingGateway {
        var paired = false
        var endpoint: String? = null
        var pairCalls = 0
        override suspend fun isPaired(scope: OperationalScope) = paired
        override fun pairedEndpoint(scope: OperationalScope) = endpoint
        override suspend fun pair(scope: OperationalScope, endpoint: String, token: String) {
            pairCalls++
            paired = true
            this.endpoint = endpoint
        }
        override suspend fun unpair(scope: OperationalScope) {
            paired = false
            endpoint = null
        }
    }

    @Test fun pairFlowHidesTokenFormAfterPairing() {
        val fake = FakeGateway()
        compose.setContent {
            CloudSyncScreen(scope, onBack = {}, viewModel = SyncStatusViewModel(fake))
        }
        compose.onNodeWithText("Pair device").assertIsNotEnabled()
        compose.onNodeWithText("VPS endpoint").performTextInput("https://vps.example")
        compose.onNodeWithText("Pairing token").performTextInput("secret-token")
        compose.onNodeWithText("Pair device").assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.paired }
        compose.onNodeWithText("Paired with https://vps.example").assertExists()
        compose.onNodeWithText("Unpair device").performClick()
        compose.waitUntil(5000) { !fake.paired }
        compose.runOnIdle { assertEquals(1, fake.pairCalls) }
        compose.onNodeWithText("Pair device").assertExists()
    }

    @Test fun backNavigation() {
        val fake = FakeGateway()
        var backCount = 0
        compose.setContent {
            CloudSyncScreen(scope, onBack = { backCount++ }, viewModel = SyncStatusViewModel(fake))
        }
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }
}
