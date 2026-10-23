package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTotalsCalculatorTest {
    private val pkr = CurrencyCode.of("PKR")
    private val salesTaxExclusive = TaxScheme(
        name = "Sales Tax",
        inclusive = false,
        components = listOf(TaxComponent("Sales Tax", Percentage.of("17"))),
    )
    private val vatInclusive = TaxScheme(
        name = "VAT",
        inclusive = true,
        components = listOf(TaxComponent("VAT", Percentage.of("5"))),
    )

    private fun line(qty: String, rate: String, discount: Discount? = null, scheme: TaxScheme? = null) =
        DocumentLineInput(Quantity.of(qty), Money.of(rate, pkr), discount, scheme)

    @Test
    fun exclusiveTaxLineTotalsQtyTimesRatePlusTax() {
        val totals = DocumentTotalsCalculator.totalLine(line("2", "500", scheme = salesTaxExclusive))
        assertEquals(Money.of("1000", pkr), totals.grossAmount)
        assertEquals(Money.of("1000", pkr), totals.taxableBase)
        assertEquals(Money.of("170", pkr), totals.totalTax)
        assertEquals(Money.of("1170", pkr), totals.lineTotal)
    }

    @Test
    fun linePercentDiscountAppliesBeforeTax() {
        val totals = DocumentTotalsCalculator.totalLine(
            line("1", "1000", discount = Discount.Percent(Percentage.of("10")), scheme = salesTaxExclusive)
        )
        assertEquals(Money.of("100", pkr), totals.discountAmount)
        assertEquals(Money.of("900", pkr), totals.taxableBase)
        assertEquals(Money.of("153", pkr), totals.totalTax)
        assertEquals(Money.of("1053", pkr), totals.lineTotal)
    }

    @Test
    fun fixedDiscountLargerThanTheLineClampsAtTheGross() {
        val totals = DocumentTotalsCalculator.totalLine(
            line("1", "100", discount = Discount.Fixed(Money.of("150", pkr)), scheme = salesTaxExclusive)
        )
        assertEquals(Money.of("100", pkr), totals.discountAmount)
        assertEquals(Money.of("0", pkr), totals.totalTax)
        assertEquals(Money.of("0", pkr), totals.lineTotal)
    }

    @Test
    fun inclusiveLineExtractsItsBaseAndTax() {
        val totals = DocumentTotalsCalculator.totalLine(line("1", "1050", scheme = vatInclusive))
        assertEquals(Money.of("1000", pkr), totals.taxableBase)
        assertEquals(Money.of("50", pkr), totals.totalTax)
        assertEquals(Money.of("1050", pkr), totals.lineTotal)
    }

    @Test
    fun untaxedLineHasNoTaxAndTotalsItsDiscountedAmount() {
        val totals = DocumentTotalsCalculator.totalLine(
            line("3", "200", discount = Discount.Percent(Percentage.of("5")))
        )
        assertEquals(Money.of("600", pkr), totals.grossAmount)
        assertEquals(Money.of("30", pkr), totals.discountAmount)
        assertEquals(Money.of("0", pkr), totals.totalTax)
        assertEquals(Money.of("570", pkr), totals.lineTotal)
    }

    @Test
    fun invoiceDiscountAppliesToTheLineTotalAndLeavesTaxUnchanged() {
        val document = DocumentTotalsCalculator.totalDocument(
            lines = listOf(line("2", "500", scheme = salesTaxExclusive)),
            invoiceDiscount = Discount.Percent(Percentage.of("10")),
        )
        assertEquals(Money.of("117", pkr), document.invoiceDiscountAmount)
        assertEquals(Money.of("170", pkr), document.taxTotal)
        assertEquals(Money.of("1053", pkr), document.grandTotal)
    }

    @Test
    fun taxableChargeAddsItsOwnTaxAndNonTaxableChargeDoesNot() {
        val document = DocumentTotalsCalculator.totalDocument(
            lines = listOf(line("1", "1000")),
            charges = listOf(
                AdditionalChargeInput("Delivery", Money.of("200", pkr), salesTaxExclusive),
                AdditionalChargeInput("Packing", Money.of("100", pkr)),
            ),
        )
        assertEquals(Money.of("34", pkr), document.taxTotal)
        assertEquals(Money.of("334", pkr), document.chargesTotal)
        assertEquals(Money.of("1300", pkr), document.taxableBaseTotal)
        assertEquals(Money.of("1334", pkr), document.grandTotal)
    }

    @Test
    fun taxComponentsAggregateAcrossLines() {
        val document = DocumentTotalsCalculator.totalDocument(
            lines = listOf(
                line("1", "1000", scheme = salesTaxExclusive),
                line("1", "1000", scheme = salesTaxExclusive),
            ),
        )
        assertEquals(1, document.taxComponents.size)
        assertEquals(Money.of("340", pkr), document.taxComponents[0].amount)
        assertEquals(Money.of("340", pkr), document.taxTotal)
        assertEquals(Money.of("2340", pkr), document.grandTotal)
    }

    @Test
    fun roundOffReportsTheDifferenceAndRoundsTheGrandTotal() {
        val down = DocumentTotalsCalculator.totalDocument(
            lines = listOf(line("1", "100.40")),
            roundOff = true,
        )
        assertEquals(Money.of("-0.40", pkr), down.roundOffAmount)
        assertEquals(Money.of("100", pkr), down.grandTotal)

        val up = DocumentTotalsCalculator.totalDocument(
            lines = listOf(line("1", "100.60")),
            roundOff = true,
        )
        assertEquals(Money.of("0.40", pkr), up.roundOffAmount)
        assertEquals(Money.of("101", pkr), up.grandTotal)
    }

    @Test
    fun emptyDocumentNeedsACurrencyHintAndTotalsZero() {
        val document = DocumentTotalsCalculator.totalDocument(
            lines = emptyList(),
            currencyHint = Money.zero(pkr),
        )
        assertEquals(Money.of("0", pkr), document.grandTotal)
        assertEquals(Money.of("0", pkr), document.taxTotal)
    }
}
