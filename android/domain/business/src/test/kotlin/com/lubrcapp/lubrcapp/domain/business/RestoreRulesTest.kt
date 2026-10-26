package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreRulesTest {

    private fun validSnapshot(): BackupSnapshot = BackupSnapshotOps.create(
        "2026-10-27",
        "Ali Autos",
        mapOf(
            "customers" to listOf(mapOf("Name" to "Ali Autos")),
            "items" to listOf(mapOf("Name" to "Shell HX7"), mapOf("Name" to "Castrol GTX")),
        ),
    )

    private fun invalidSnapshot(): BackupSnapshot = BackupSnapshot(
        BackupManifest("2026-10-27", "Ali Autos", mapOf("customers" to 9, "items" to 2)),
        validSnapshot().tables,
    )

    @Test
    fun previewShowsWizardFields() {
        val preview = RestoreRules.preview(validSnapshot())
        assertEquals("2026-10-27", preview.backupDate)
        assertEquals("Ali Autos", preview.businessName)
        assertEquals(mapOf("customers" to 1, "items" to 2), preview.recordCounts)
        assertEquals(3, preview.totalRecords)
        assertTrue(preview.problems.isEmpty())
    }

    @Test
    fun validPreviewDecidesReady() {
        val decision = RestoreRules.decide(RestoreRules.preview(validSnapshot()))
        assertTrue(decision is RestoreDecision.Ready)
    }

    @Test
    fun invalidPreviewDecidesBlockedWithProblems() {
        val decision = RestoreRules.decide(RestoreRules.preview(invalidSnapshot()))
        assertTrue(decision is RestoreDecision.Blocked)
        assertTrue((decision as RestoreDecision.Blocked).problems.isNotEmpty())
    }

    @Test
    fun validSnapshotNeedsConfirmation() {
        val preview = RestoreRules.preview(validSnapshot())
        assertFalse(RestoreRules.canRestore(preview, confirmed = false))
        assertTrue(RestoreRules.canRestore(preview, confirmed = true))
    }

    @Test
    fun invalidSnapshotNeverRestoresEvenWhenConfirmed() {
        val preview = RestoreRules.preview(invalidSnapshot())
        assertFalse(RestoreRules.canRestore(preview, confirmed = true))
    }
}
