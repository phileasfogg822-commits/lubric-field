package com.lubrcapp.lubrcapp.feature.business

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.domain.business.BackupManifest
import com.lubrcapp.lubrcapp.domain.business.BackupSnapshot
import com.lubrcapp.lubrcapp.domain.business.BackupSnapshotOps
import com.lubrcapp.lubrcapp.domain.business.RestoreRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun validPreview() = RestoreRules.preview(
        BackupSnapshotOps.create(
            "2026-10-27",
            "Ali Autos",
            mapOf("sales_documents" to listOf(mapOf("Number" to "INV-1"))),
        ),
    )

    private fun invalidPreview() = RestoreRules.preview(
        BackupSnapshot(
            BackupManifest("2026-10-27", "Ali Autos", mapOf("sales_documents" to 9)),
            mapOf("sales_documents" to listOf(mapOf("Number" to "INV-1"))),
        ),
    )

    @Test
    fun confirmRowTogglesThroughItsLabel() {
        var captured: Boolean? = null
        compose.setContent {
            BackupScreen(validPreview(), confirmed = false, onConfirmedChange = { captured = it })
        }
        compose.onNodeWithText("Confirm overwrite").performClick()
        assertEquals(true, captured)
    }

    @Test
    fun restoreDisabledUntilConfirmed() {
        compose.setContent {
            BackupScreen(validPreview(), confirmed = false, onConfirmedChange = {}, onRestore = {})
        }
        compose.onNodeWithText("Restore").assertIsNotEnabled()
    }

    @Test
    fun restoreEnabledAndFiresWhenConfirmed() {
        var clicks = 0
        compose.setContent {
            BackupScreen(validPreview(), confirmed = true, onConfirmedChange = {}, onRestore = { clicks += 1 })
        }
        compose.onNodeWithText("Restore").assertIsEnabled()
        compose.onNodeWithText("Restore").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun invalidPreviewBlocksRestoreAndShowsProblems() {
        val preview = invalidPreview()
        assertTrue(preview.problems.isNotEmpty())
        compose.setContent {
            BackupScreen(preview, confirmed = true, onConfirmedChange = {}, onRestore = {})
        }
        compose.onNodeWithText("Restore").assertIsNotEnabled()
        compose.onNodeWithText(preview.problems.single()).assertIsDisplayed()
    }
}
