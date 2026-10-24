package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesDocumentEngineTest {
    private val pkr = CurrencyCode.of("PKR")

    private fun line(
        position: Int = 0,
        quantity: String = "1",
        rate: String = "1000",
        discountKind: String? = null,
        discountValue: String? = null,
        taxInclusive: Boolean = false,
        taxComponents: List<SalesTaxComponent> = emptyList(),
    ) = SalesDocumentLine(
        id = "line-$position",
        orderId = "doc-1",
        position = position,
        quantity = BigDecimal(quantity),
        rate = BigDecimal(rate),
        discountKind = discountKind,
        discountValue = discountValue?.let { BigDecimal(it) },
        taxInclusive = taxInclusive,
        taxComponents = taxComponents,
    )

    @Test
    fun storedLinesProduceEngineTotals() {
        val totals = SalesDocumentEngine.totals(
            lines = listOf(
                line(
                    quantity = "2",
                    rate = "500",
                    taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
                )
            ),
            currency = pkr,
        )
        assertEquals(Money.of("1000", pkr), totals.grossBeforeDiscounts)
        assertEquals(Money.of("170", pkr), totals.taxTotal)
        assertEquals(Money.of("1170", pkr), totals.grandTotal)
    }

    @Test
    fun percentDiscountSnapshotAppliesBeforeTax() {
        val totals = SalesDocumentEngine.totals(
            lines = listOf(
                line(
                    discountKind = "PERCENT",
                    discountValue = "10",
                    taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
                )
            ),
            currency = pkr,
        )
        assertEquals(Money.of("100", pkr), totals.lineDiscountsTotal)
        assertEquals(Money.of("153", pkr), totals.taxTotal)
        assertEquals(Money.of("1053", pkr), totals.grandTotal)
    }

    @Test
    fun inclusiveSnapshotExtractsTaxAndUntaxedLinesPassThrough() {
        val totals = SalesDocumentEngine.totals(
            lines = listOf(
                line(
                    position = 0,
                    rate = "1050",
                    taxInclusive = true,
                    taxComponents = listOf(SalesTaxComponent("VAT", BigDecimal("5"))),
                ),
                line(position = 1, rate = "200"),
            ),
            currency = pkr,
        )
        assertEquals(Money.of("50", pkr), totals.taxTotal)
        assertEquals(Money.of("1200", pkr), totals.taxableBaseTotal)
        assertEquals(Money.of("1250", pkr), totals.grandTotal)
    }

    @Test
    fun anUnknownDiscountKindIsIgnoredRatherThanCrashing() {
        val input = SalesDocumentEngine.lineInput(
            line(discountKind = "SCHEME", discountValue = "10"),
            pkr,
        )
        assertEquals(null, input.discount)
    }
}
