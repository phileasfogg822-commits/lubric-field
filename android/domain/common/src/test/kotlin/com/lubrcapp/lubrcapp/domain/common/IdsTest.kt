package com.lubrcapp.lubrcapp.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdsTest {
    @Test
    fun generatedIdsAreNonBlankAndUnique() {
        val ids = (1..1000).map { UuidIdGenerator.generate() }
        assertEquals(1000, ids.toSet().size)
        ids.forEach { require(it.isNotBlank()) }
    }

    @Test
    fun typedIdsRemainDistinctTypes() {
        val raw = UuidIdGenerator.generate()
        assertNotEquals(BusinessId(raw) as Any, UserId(raw) as Any)
    }

    @Test
    fun blankIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BusinessId("") }
    }
}
