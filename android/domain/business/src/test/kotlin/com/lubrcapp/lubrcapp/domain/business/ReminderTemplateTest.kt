package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTemplateTest {

    @Test
    fun specExampleRendersFully() {
        val template = ReminderTemplate(
            id = "t1",
            body = "Hello {party},\n\nInvoice {invoice} for Rs {amount} is overdue.",
        )
        assertEquals(
            "Hello Ali Autos,\n\nInvoice INV-0182 for Rs 25,800 is overdue.",
            ReminderTemplateOps.render(
                template,
                mapOf("party" to "Ali Autos", "invoice" to "INV-0182", "amount" to "25,800"),
            ),
        )
    }

    @Test
    fun unknownPlaceholderIsLeftIntact() {
        val template = ReminderTemplate(id = "t2", body = "Hello {party}, invoice {invoice} due.")
        assertEquals(
            "Hello Ali Autos, invoice {invoice} due.",
            ReminderTemplateOps.render(template, mapOf("party" to "Ali Autos")),
        )
    }

    @Test
    fun repeatedPlaceholderRendersEverywhere() {
        val template = ReminderTemplate(id = "t3", body = "{party} owes {amount}; {party} please pay {amount}.")
        assertEquals(
            "Ali owes 100; Ali please pay 100.",
            ReminderTemplateOps.render(template, mapOf("party" to "Ali", "amount" to "100")),
        )
    }

    @Test
    fun emptyValuesLeaveBodyUnchanged() {
        val template = ReminderTemplate(id = "t4", body = "Pay {amount} by {date}.")
        assertEquals("Pay {amount} by {date}.", ReminderTemplateOps.render(template, emptyMap()))
    }

    @Test
    fun emptyBodyRendersEmpty() {
        assertEquals("", ReminderTemplateOps.render(ReminderTemplate(id = "t5"), mapOf("a" to "b")))
    }

    @Test
    fun extraValuesAreIgnored() {
        val template = ReminderTemplate(id = "t6", body = "Hi {party}.")
        assertEquals(
            "Hi Ali.",
            ReminderTemplateOps.render(template, mapOf("party" to "Ali", "unused" to "x")),
        )
    }

    @Test
    fun placeholdersAreExtractedDeduplicated() {
        val template = ReminderTemplate(id = "t7", body = "{party} {invoice} {party} {amount}")
        assertEquals(
            setOf("party", "invoice", "amount"),
            ReminderTemplateOps.placeholders(template),
        )
    }

    @Test
    fun spacedBracesAreLiteralText() {
        val template = ReminderTemplate(id = "t8", body = "Use { party } literally.")
        assertEquals(
            "Use { party } literally.",
            ReminderTemplateOps.render(template, mapOf("party" to "Ali")),
        )
        assertTrue(ReminderTemplateOps.placeholders(template).isEmpty())
    }
}
