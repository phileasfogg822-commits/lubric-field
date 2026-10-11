package com.lubrcapp.lubrcapp.core.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMutationPolicyTest {

    @Test
    fun protectedHistoryNeverAllowsDirectEditOrDelete() {
        assertFalse(HistoryMutationPolicy.isAllowed(HistoryMutation.DIRECT_EDIT))
        assertFalse(HistoryMutationPolicy.isAllowed(HistoryMutation.DIRECT_DELETE))
    }

    @Test
    fun appendReverseAndCorrectAreTheOnlyAllowedMutationShapes() {
        assertTrue(HistoryMutationPolicy.isAllowed(HistoryMutation.APPEND))
        assertTrue(HistoryMutationPolicy.isAllowed(HistoryMutation.REVERSE))
        assertTrue(HistoryMutationPolicy.isAllowed(HistoryMutation.CORRECT))
    }

    @Test
    fun reversalAndCorrectionRequireReasonsForEveryProtectedRecordType() {
        ProtectedHistoryType.entries.forEach { type ->
            assertTrue(
                runCatching {
                    HistoryMutationPolicy.requireAllowed(
                        type,
                        HistoryMutation.REVERSE,
                        reason = "",
                    )
                }.isFailure
            )
            assertTrue(
                runCatching {
                    HistoryMutationPolicy.requireAllowed(
                        type,
                        HistoryMutation.CORRECT,
                        reason = null,
                    )
                }.isFailure
            )
            HistoryMutationPolicy.requireAllowed(
                type,
                HistoryMutation.REVERSE,
                reason = "customer-approved correction",
            )
        }
    }
}
