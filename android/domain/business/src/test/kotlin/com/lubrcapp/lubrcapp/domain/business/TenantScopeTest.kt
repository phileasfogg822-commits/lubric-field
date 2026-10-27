package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantScopeTest {

    private val scope = TenantScope("t1", "b1")

    @Test
    fun scopeRejectsBlankTenantOrBusiness() {
        assertThrows(IllegalArgumentException::class.java) { TenantScope("", "b1") }
        assertThrows(IllegalArgumentException::class.java) { TenantScope("t1", "  ") }
    }

    @Test
    fun exactPairIsVisible() {
        assertTrue(TenantScopeOps.isVisible(TenantScopedRecord("r1", "t1", "b1"), scope))
    }

    @Test
    fun eitherFieldMismatchHidesTheRecord() {
        assertFalse(TenantScopeOps.isVisible(TenantScopedRecord("r1", "t2", "b1"), scope))
        assertFalse(TenantScopeOps.isVisible(TenantScopedRecord("r1", "t1", "b2"), scope))
        assertFalse(TenantScopeOps.isVisible(TenantScopedRecord("r1", "t2", "b2"), scope))
    }

    @Test
    fun visibleFiltersMixedListPreservingOrder() {
        val records = listOf(
            TenantScopedRecord("r1", "t1", "b1"),
            TenantScopedRecord("r2", "t2", "b1"),
            TenantScopedRecord("r3", "t1", "b1"),
            TenantScopedRecord("r4", "t1", "b2"),
        )
        assertEquals(
            listOf("r1", "r3"),
            TenantScopeOps.visible(records, scope).map { it.id },
        )
    }

    @Test
    fun visibleOnEmptyListIsEmpty() {
        assertTrue(TenantScopeOps.visible(emptyList(), scope).isEmpty())
    }

    @Test
    fun requireVisiblePassesOnMatch() {
        TenantScopeOps.requireVisible(TenantScopedRecord("r1", "t1", "b1"), scope)
    }

    @Test
    fun requireVisibleThrowsOutsideScope() {
        assertThrows(IllegalArgumentException::class.java) {
            TenantScopeOps.requireVisible(TenantScopedRecord("r1", "t2", "b1"), scope)
        }
    }

    @Test
    fun differentScopesSeeDifferentRecords() {
        val records = listOf(
            TenantScopedRecord("r1", "t1", "b1"),
            TenantScopedRecord("r2", "t2", "b2"),
        )
        assertEquals(
            listOf("r2"),
            TenantScopeOps.visible(records, TenantScope("t2", "b2")).map { it.id },
        )
    }
}
