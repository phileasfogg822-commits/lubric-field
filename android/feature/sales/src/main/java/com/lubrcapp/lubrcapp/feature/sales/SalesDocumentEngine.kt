package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.AdditionalChargeInput
import com.lubrcapp.lubrcapp.domain.accounting.Discount
import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineInput
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotalsCalculator
import com.lubrcapp.lubrcapp.domain.accounting.TaxComponent
import com.lubrcapp.lubrcapp.domain.accounting.TaxScheme
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity

/**
 * Phase 18.1 — the bridge from stored document records to the
 * Phase 17 totals engine. This is the ONLY place stored lines are
 * translated into engine inputs: every total shown or posted for
 * a quotation or order comes from here, so the engine's rules
 * (discount order, tax modes, rounding) apply identically
 * everywhere. The document's currency is the business currency,
 * supplied by the caller — it is not stored on the document.
 */
object SalesDocumentEngine {

    fun lineInput(line: SalesDocumentLine, currency: CurrencyCode): DocumentLineInput =
        DocumentLineInput(
            quantity = Quantity.of(line.quantity),
            rate = Money.of(line.rate, currency),
            discount = discount(line, currency),
            taxScheme = taxScheme(line),
        )

    fun totals(
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
        charges: List<AdditionalChargeInput> = emptyList(),
        invoiceDiscount: Discount? = null,
        roundOff: Boolean = false,
    ): DocumentTotals =
        DocumentTotalsCalculator.totalDocument(
            lines = lines.sortedBy { it.position }.map { lineInput(it, currency) },
            charges = charges,
            invoiceDiscount = invoiceDiscount,
            roundOff = roundOff,
            currencyHint = Money.zero(currency),
        )

    private fun discount(line: SalesDocumentLine, currency: CurrencyCode): Discount? {
        val value = line.discountValue ?: return null
        return when (line.discountKind) {
            "PERCENT" -> Discount.Percent(Percentage.of(value))
            "FIXED" -> Discount.Fixed(Money.of(value, currency))
            else -> null
        }
    }

    private fun taxScheme(line: SalesDocumentLine): TaxScheme? {
        if (line.taxComponents.isEmpty()) return null
        return TaxScheme(
            name = line.taxComponents.joinToString(" + ") { it.name },
            inclusive = line.taxInclusive,
            components = line.taxComponents.map { TaxComponent(it.name, Percentage.of(it.ratePercent)) },
        )
    }
}
