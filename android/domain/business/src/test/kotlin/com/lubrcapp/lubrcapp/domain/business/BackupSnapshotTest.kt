package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSnapshotTest {

    private fun tables(): Map<String, List<Map<String, String>>> = mapOf(
        "customers" to listOf(mapOf("Name" to "Ali Autos"), mapOf("Name" to "Bilal Traders")),
        "items" to listOf(mapOf("Name" to "Shell HX7")),
    )

    @Test
    fun createComputesCountsFromTables() {
        val snapshot = BackupSnapshotOps.create("2026-10-27", "Ali Autos", tables())
        assertEquals("2026-10-27", snapshot.manifest.backupDate)
        assertEquals("Ali Autos", snapshot.manifest.businessName)
        assertEquals(mapOf("customers" to 2, "items" to 1), snapshot.manifest.recordCounts)
    }

    @Test
    fun fileNameFollowsSpecExample() {
        assertEquals(
            "business_2026-09-30.busbackup",
            BackupSnapshotOps.fileName("2026-09-30"),
        )
    }

    @Test
    fun fileNameThrowsOnNonIsoDate() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotOps.fileName("30-09-2026")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotOps.fileName("")
        }
    }

    @Test
    fun createdSnapshotVerifiesClean() {
        val snapshot = BackupSnapshotOps.create("2026-10-27", "Ali Autos", tables())
        assertTrue(BackupSnapshotOps.verify(snapshot).isEmpty())
    }

    @Test
    fun verifyFlagsCountMismatch() {
        val snapshot = BackupSnapshot(
            BackupManifest("2026-10-27", "Ali Autos", mapOf("customers" to 5, "items" to 1)),
            tables(),
        )
        val problems = BackupSnapshotOps.verify(snapshot)
        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("customers"))
    }

    @Test
    fun verifyFlagsBlankBusinessAndDate() {
        val snapshot = BackupSnapshot(
            BackupManifest("", "", mapOf("customers" to 2, "items" to 1)),
            tables(),
        )
        val problems = BackupSnapshotOps.verify(snapshot)
        assertEquals(2, problems.size)
    }

    @Test
    fun verifyFlagsManifestTableMissingFromSnapshot() {
        val snapshot = BackupSnapshot(
            BackupManifest("2026-10-27", "Ali Autos", mapOf("customers" to 2, "orders" to 1)),
            tables(),
        )
        val problems = BackupSnapshotOps.verify(snapshot)
        assertTrue(problems.any { it.contains("orders") })
    }

    @Test
    fun verifyFlagsTableMissingFromManifest() {
        val snapshot = BackupSnapshot(
            BackupManifest("2026-10-27", "Ali Autos", mapOf("customers" to 2)),
            tables(),
        )
        val problems = BackupSnapshotOps.verify(snapshot)
        assertTrue(problems.any { it.contains("items") })
    }

    @Test
    fun emptyTablesVerifyClean() {
        val snapshot = BackupSnapshotOps.create("2026-10-27", "Ali Autos", emptyMap())
        assertTrue(BackupSnapshotOps.verify(snapshot).isEmpty())
        assertTrue(snapshot.manifest.recordCounts.isEmpty())
    }
}
