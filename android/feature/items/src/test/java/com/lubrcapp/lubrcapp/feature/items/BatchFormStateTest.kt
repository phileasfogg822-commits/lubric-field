package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 16.4: the batch form guards item, batch number and numeric fields. */
class BatchFormStateTest {

    private fun validForm(): BatchFormState {
        val form = BatchFormState()
        form.itemId = "i-1"
        form.batchNumber = " B-1 "
        form.expiryDate = "2027-01-14"
        form.quantity = "48"
        return form
    }

    @Test fun buildsBatchTrimmingTextFields() {
        val batch = validForm().toBatch("bt-1")
        assertEquals("B-1", batch?.batchNumber)
        assertEquals("2027-01-14", batch?.expiryDate)
        assertEquals(BigDecimal("48"), batch?.quantity)
        assertEquals(null, batch?.manufacturingDate)
    }

    @Test fun rejectsUnpickedItemAndBlankBatchNumber() {
        val form = validForm()
        form.itemId = null
        assertNull(form.toBatch("bt-1"))
        form.itemId = "i-1"
        form.batchNumber = "   "
        assertNull(form.toBatch("bt-1"))
    }

    @Test fun rejectsNonNumericAmounts() {
        val form = validForm()
        form.quantity = "many"
        assertNull(form.toBatch("bt-1"))
        form.quantity = "48"
        form.saleRate = "soon"
        assertNull(form.toBatch("bt-1"))
    }

    @Test fun loadRoundTripsThroughForm() {
        val batch = Batch(
            id = "bt-5",
            itemId = "i-2",
            batchNumber = "B-9",
            manufacturingDate = "2026-02-01",
            purchaseRate = BigDecimal("10.5"),
            quantity = BigDecimal("3"),
        )
        val form = BatchFormState()
        form.load(batch)
        assertEquals(batch, form.toBatch("bt-5"))
    }
}
