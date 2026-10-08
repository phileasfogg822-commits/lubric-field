package com.lubrcapp.lubrcapp.domain.common

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataTest {
    private val deviceId = DeviceId("device-1")
    private val userId = UserId("user-1")

    @Test
    fun newLocalMetadataStartsAtRevisionZero() {
        val now = Instant.parse("2026-10-08T00:00:00Z")
        val metadata = EntityMetadata.newLocal(now, deviceId, userId)

        assertEquals(0L, metadata.sync.revision)
        assertEquals(SyncStatus.LOCAL_ONLY, metadata.sync.status)
        assertEquals(userId, metadata.audit.createdBy)
        assertFalse(metadata.isDeleted)
    }

    @Test
    fun softDeleteStateIsExplicit() {
        val deletedAt = Instant.parse("2026-10-08T01:00:00Z")
        val deletion = SoftDeleteMetadata(deletedAt = deletedAt, deletedBy = userId)
        assertTrue(deletion.isDeleted)
    }

    @Test
    fun negativeRevisionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncMetadata(deviceId = deviceId, revision = -1)
        }
    }

    @Test
    fun updatedAtCannotPrecedeCreatedAt() {
        assertThrows(IllegalArgumentException::class.java) {
            AuditMetadata(
                createdAt = Instant.parse("2026-10-08T01:00:00Z"),
                updatedAt = Instant.parse("2026-10-08T00:00:00Z"),
            )
        }
    }
}
