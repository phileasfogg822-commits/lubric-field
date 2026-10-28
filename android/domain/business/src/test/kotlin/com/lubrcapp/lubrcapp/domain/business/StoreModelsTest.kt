package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreModelsTest {

    private fun money(amount: String, currency: String = "PKR") =
        Money.of(amount, CurrencyCode(currency))

    private val catalog = listOf(
        StoreItem("i1", "Mobil 1", saleRate = money("500")),
        StoreItem("i2", "Shell HX7", saleRate = money("250")),
        StoreItem("i3", "Hidden Oil", saleRate = money("100"), isPublished = false),
    )

    private fun line(itemId: String, quantity: String, rate: String) =
        StoreOrderLine(itemId, Quantity.of(quantity), money(rate))

    @Test
    fun visibleShowsPublishedItemsOnly() {
        assertEquals(
            listOf("i1", "i2"),
            StorefrontOps.visible(catalog).map { it.id },
        )
    }

    @Test
    fun cleanOrderValidates() {
        val order = StoreOrder("o1", "p1", listOf(line("i1", "2", "500"), line("i2", "1", "250")))
        assertEquals(StoreOrderValidation.Valid, StorefrontOps.validate(order, catalog))
    }

    @Test
    fun unknownItemIsInvalid() {
        val order = StoreOrder("o1", "p1", listOf(line("i9", "1", "500")))
        val result = StorefrontOps.validate(order, catalog)
        assertTrue(result is StoreOrderValidation.Invalid)
        assertEquals(listOf("Unknown item i9"), (result as StoreOrderValidation.Invalid).reasons)
    }

    @Test
    fun unpublishedItemIsInvalid() {
        val order = StoreOrder("o1", "p1", listOf(line("i3", "1", "100")))
        val result = StorefrontOps.validate(order, catalog)
        assertTrue(result is StoreOrderValidation.Invalid)
        assertEquals(listOf("Unpublished item i3"), (result as StoreOrderValidation.Invalid).reasons)
    }

    @Test
    fun nonPositiveQuantityIsInvalid() {
        val order = StoreOrder("o1", "p1", listOf(line("i1", "0", "500")))
        val result = StorefrontOps.validate(order, catalog)
        assertTrue(result is StoreOrderValidation.Invalid)
        assertEquals(
            listOf("Non-positive quantity for i1"),
            (result as StoreOrderValidation.Invalid).reasons,
        )
    }

    @Test
    fun rateMismatchIsInvalid() {
        val order = StoreOrder("o1", "p1", listOf(line("i1", "1", "600")))
        val result = StorefrontOps.validate(order, catalog)
        assertTrue(result is StoreOrderValidation.Invalid)
        assertEquals(listOf("Rate mismatch for i1"), (result as StoreOrderValidation.Invalid).reasons)
    }

    @Test
    fun totalSumsLineAmounts() {
        val order = StoreOrder("o1", "p1", listOf(line("i1", "2", "500"), line("i2", "1", "250")))
        assertEquals(money("1250"), StorefrontOps.total(order))
    }

    @Test
    fun mixedCurrencyTotalThrows() {
        val order = StoreOrder(
            "o1",
            "p1",
            listOf(
                line("i1", "1", "500"),
                StoreOrderLine("i2", Quantity.of("1"), money("250", "USD")),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { StorefrontOps.total(order) }
    }
}
