package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxCalculatorTest {
    private val pkr = CurrencyCode.of("PKR")

    private fun scheme(inclusive: Boolean, vararg components: Pair<String, String>) =
        TaxScheme(
            name = "Test scheme",
            registrationNumber = "REG-1",
            inclusive = inclusive,
            components = components.map { (name, rate) -> TaxComponent(name, Percentage.of(rate)) },
        )

    @Test
    fun exclusiveSingleComponentAddsTaxOnTop() {
        val result = TaxCalculator.calculate(Money.of("1000", pkr), scheme(false, "Sales Tax" to "17"))
        assertEquals(Money.of("1000", pkr), result.taxableBase)
        assertEquals(Money.of("170", pkr), result.totalTax)
        assertEquals(Money.of("1170", pkr), result.grossAmount)
        assertEquals(1, result.components.size)
        assertEquals(Money.of("170", pkr), result.components[0].amount)
    }

    @Test
    fun exclusiveTwoComponentsAreComputedIndependently() {
        val result = TaxCalculator.calculate(
            Money.of("1000", pkr),
            scheme(false, "CGST" to "9", "SGST" to "9"),
        )
        assertEquals(Money.of("90", pkr), result.components[0].amount)
        assertEquals(Money.of("90", pkr), result.components[1].amount)
        assertEquals(Money.of("180", pkr), result.totalTax)
        assertEquals(Money.of("1180", pkr), result.grossAmount)
    }

    @Test
    fun exclusiveComponentTaxRoundsHalfUpToTwoPlaces() {
        val result = TaxCalculator.calculate(Money.of("33.33", pkr), scheme(false, "Sales Tax" to "17"))
        assertEquals(Money.of("5.67", pkr), result.totalTax)
        assertEquals(Money.of("39.00", pkr), result.grossAmount)
    }

    @Test
    fun inclusiveSingleComponentExtractsTaxFromGross() {
        val result = TaxCalculator.calculate(Money.of("1050", pkr), scheme(true, "VAT" to "5"))
        assertEquals(Money.of("1000", pkr), result.taxableBase)
        assertEquals(Money.of("50", pkr), result.totalTax)
        assertEquals(Money.of("1050", pkr), result.grossAmount)
        assertEquals(Money.of("50", pkr), result.components[0].amount)
    }

    @Test
    fun inclusiveTwoComponentsSplitTheExtractedTax() {
        val result = TaxCalculator.calculate(
            Money.of("1180", pkr),
            scheme(true, "CGST" to "9", "SGST" to "9"),
        )
        assertEquals(Money.of("1000", pkr), result.taxableBase)
        assertEquals(Money.of("180", pkr), result.totalTax)
        assertEquals(Money.of("90", pkr), result.components[0].amount)
        assertEquals(Money.of("90", pkr), result.components[1].amount)
    }

    @Test
    fun inclusiveRoundingResidueIsAbsorbedSoComponentsSumToTotalTax() {
        // Base = 100 / 1.10 = 90.91, tax = 9.09; raw shares 4.545 each round
        // to 4.55 + 4.55 = 9.10, so the largest component absorbs -0.01.
        val result = TaxCalculator.calculate(
            Money.of("100", pkr),
            scheme(true, "A" to "5", "B" to "5"),
        )
        assertEquals(Money.of("90.91", pkr), result.taxableBase)
        assertEquals(Money.of("9.09", pkr), result.totalTax)
        val componentSum = result.components.fold(Money.zero(pkr)) { acc, part -> acc + part.amount }
        assertEquals(result.totalTax, componentSum)
        assertEquals(Money.of("100", pkr), result.taxableBase + result.totalTax)
    }

    @Test
    fun inclusiveExtractsFromAnAmountThatDoesNotDivideCleanly() {
        val result = TaxCalculator.calculate(Money.of("100", pkr), scheme(true, "Sales Tax" to "17"))
        assertEquals(Money.of("85.47", pkr), result.taxableBase)
        assertEquals(Money.of("14.53", pkr), result.totalTax)
        assertEquals(Money.of("14.53", pkr), result.components[0].amount)
    }

    @Test
    fun emptySchemeChargesNoTaxInEitherMode() {
        val exclusive = TaxCalculator.calculate(Money.of("500", pkr), scheme(false))
        assertEquals(Money.of("0", pkr), exclusive.totalTax)
        assertEquals(Money.of("500", pkr), exclusive.grossAmount)

        val inclusive = TaxCalculator.calculate(Money.of("500", pkr), scheme(true))
        assertEquals(Money.of("0", pkr), inclusive.totalTax)
        assertEquals(Money.of("500", pkr), inclusive.taxableBase)
        assertEquals(Money.of("500", pkr), inclusive.grossAmount)
    }

    @Test
    fun zeroRatedComponentsChargeNoTax() {
        val result = TaxCalculator.calculate(Money.of("500", pkr), scheme(true, "Zero" to "0"))
        assertEquals(Money.of("0", pkr), result.totalTax)
        assertEquals(Money.of("500", pkr), result.taxableBase)
        assertEquals(Money.of("0", pkr), result.components[0].amount)
    }

    @Test
    fun schemeTotalRateSumsComponentPoints() {
        assertEquals(Percentage.of("18"), scheme(false, "CGST" to "9", "SGST" to "9").totalRate)
        assertEquals(Percentage.ZERO, scheme(false).totalRate)
    }
}
