package com.lubrcapp.lubrcapp.domain.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeliveryExecutionTest {

    @Test
    fun deliveredKeepsProofFields() {
        val proof = DeliveryProof(
            receiverName = "Ali",
            signatureUri = "content://sign/1",
            photoUri = "content://photo/1",
            deliveryNote = "Left at shop",
        )
        val execution = DeliveryExecutionOps.delivered("dc-1", proof)
        assertEquals(DeliveryOutcome.DELIVERED, execution.outcome)
        assertEquals("Ali", execution.proof.receiverName)
        assertEquals("content://sign/1", execution.proof.signatureUri)
        assertEquals("content://photo/1", execution.proof.photoUri)
        assertEquals("Left at shop", execution.proof.deliveryNote)
        assertEquals(null, execution.reason)
        DeliveryExecutionOps.validate(execution)
    }

    @Test
    fun deliveredDefaultsToEmptyProof() {
        val execution = DeliveryExecutionOps.delivered("dc-1")
        assertEquals("", execution.proof.receiverName)
        DeliveryExecutionOps.validate(execution)
    }

    @Test
    fun failedKeepsReasonAndNote() {
        val execution = DeliveryExecutionOps.failed(
            "dc-1",
            DeliveryFailureReason.SHOP_CLOSED,
            "Shop shut at noon",
        )
        assertEquals(DeliveryOutcome.FAILED, execution.outcome)
        assertEquals(DeliveryFailureReason.SHOP_CLOSED, execution.reason)
        assertEquals("Shop shut at noon", execution.proof.deliveryNote)
        DeliveryExecutionOps.validate(execution)
    }

    @Test
    fun partialKeepsReason() {
        val execution = DeliveryExecutionOps.partial(
            "dc-1",
            DeliveryFailureReason.PARTIAL_DELIVERY,
            "2 of 10 cartons refused",
        )
        assertEquals(DeliveryOutcome.PARTIAL, execution.outcome)
        assertEquals(DeliveryFailureReason.PARTIAL_DELIVERY, execution.reason)
        DeliveryExecutionOps.validate(execution)
    }

    @Test
    fun allSpecReasonsExist() {
        assertEquals(
            listOf(
                DeliveryFailureReason.PARTIAL_DELIVERY,
                DeliveryFailureReason.CUSTOMER_REJECTED,
                DeliveryFailureReason.SHOP_CLOSED,
                DeliveryFailureReason.INCORRECT_ORDER,
                DeliveryFailureReason.OTHER,
            ),
            DeliveryFailureReason.entries.toList(),
        )
    }

    @Test
    fun validateThrowsWhenFailedOrPartialHasNoReason() {
        assertThrows(IllegalArgumentException::class.java) {
            DeliveryExecutionOps.validate(
                DeliveryExecution("dc-1", DeliveryOutcome.FAILED),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeliveryExecutionOps.validate(
                DeliveryExecution("dc-1", DeliveryOutcome.PARTIAL),
            )
        }
    }

    @Test
    fun validateThrowsWhenDeliveredCarriesAReason() {
        assertThrows(IllegalArgumentException::class.java) {
            DeliveryExecutionOps.validate(
                DeliveryExecution(
                    "dc-1",
                    DeliveryOutcome.DELIVERED,
                    reason = DeliveryFailureReason.OTHER,
                ),
            )
        }
    }

    @Test
    fun executionKeepsChallanId() {
        assertEquals("dc-9", DeliveryExecutionOps.delivered("dc-9").challanId)
    }
}
