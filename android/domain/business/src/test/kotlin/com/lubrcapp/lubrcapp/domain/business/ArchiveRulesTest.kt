package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRulesTest {

    @Test
    fun archiveSetsFlag() {
        val archived = ArchiveRules.archive(Archivable("Shell HX7"))
        assertTrue(archived.isArchived)
        assertEquals("Shell HX7", archived.item)
    }

    @Test
    fun archiveIsIdempotent() {
        val once = ArchiveRules.archive(Archivable("Shell HX7"))
        val twice = ArchiveRules.archive(once)
        assertEquals(once, twice)
    }

    @Test
    fun restoreClearsFlag() {
        val restored = ArchiveRules.restore(Archivable("Shell HX7", isArchived = true))
        assertFalse(restored.isArchived)
    }

    @Test
    fun restoreOfLiveRecordIsNoOp() {
        val live = Archivable("Shell HX7")
        assertEquals(live, ArchiveRules.restore(live))
    }

    @Test
    fun visibleExcludesArchived() {
        val records = listOf(
            Archivable("Shell HX7"),
            Archivable("Castrol GTX", isArchived = true),
        )
        assertEquals(listOf(Archivable("Shell HX7")), ArchiveRules.visible(records))
    }

    @Test
    fun historyKeepsArchivedRecords() {
        val records = listOf(
            Archivable("Shell HX7"),
            Archivable("Castrol GTX", isArchived = true),
        )
        assertEquals(records, ArchiveRules.history(records))
    }

    @Test
    fun originalRecordUnchanged() {
        val live = Archivable("Shell HX7")
        ArchiveRules.archive(live)
        assertFalse(live.isArchived)
    }
}
