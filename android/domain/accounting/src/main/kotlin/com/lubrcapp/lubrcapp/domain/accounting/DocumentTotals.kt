package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import java.math.RoundingMode

/**
 * Document totals engine (master spec §52 invoice item row, §53
 * discounts, §54 additional charges). Pure math over plain data: the
 * same engine totals a sales invoice, a purchase bill, a quotation —
 * document phases supply lines and read totals; they never re-derive
 * tax or discount rules themselves.
 *
 * Computation order (the contract, pinned by tests):
 * 1. Line gross = quantity × rate, rounded to 2dp HALF_UP per line.
 * 2. Line discount (§53: percent of gross, or a fixed amount) — a
 *    discount can never make a line negative; it clamps at the gross.
 * 3. Line tax via [TaxCalculator] on the discounted amount. The entered
 *    rate is tax-inclusive exactly when the line's scheme is inclusive.
 * 4. Additional charges (§54), each with its own optional scheme, are
 *    totalled the same way (base, tax, gross) and added after lines.
 * 5. Invoice-level discount (§53) applies to the sum of line totals
 *    AFTER line discounts and tax — a bottom-line commercial discount.
 *    It deliberately does NOT recompute tax; the tax figures stay
 *    exactly what step 3 produced. (A phase that needs discount-before-
 *    tax semantics must say so explicitly and revisit this contract.)
 * 6. Optional round-off to whole currency units, last: the difference
 *    is reported as [DocumentTotals.roundOffAmount], never hidden.
 *
 * All money in one document must share one currency; [Money] arithmetic
 * throws on a mismatch, which is the engine's currency guard.
 */
sealed interface Discount {
    data class Percent(val rate: Percentage) : Discount
    data class Fixed(val amount: Money) : Discount
}

data class DocumentLineInput(
    val quantity: Quantity,
    val rate: Money,
    val discount: Discount? = null,
    val taxScheme: TaxScheme? = null,
)

data class AdditionalChargeInput(
    val name: String,
    val amount: Money,
    val taxScheme: TaxScheme? = null,
) {
    init {
        require(name.isNotBlank()) { "Charge name is required" }
    }
}

data class DocumentLineTotals(
    val grossAmount: Money,
    val discountAmount: Money,
    val taxableBase: Money,
    val taxComponents: List<TaxComponentAmount>,
    val totalTax: Money,
    val lineTotal: Money,
)

data class ChargeTotals(
    val name: String,
    val taxableBase: Money,
    val taxComponents: List<TaxComponentAmount>,
    val totalTax: Money,
    val grossAmount: Money,
)

data class DocumentTotals(
    val lines: List<DocumentLineTotals>,
    val charges: List<ChargeTotals>,
    val grossBeforeDiscounts: Money,
    val lineDiscountsTotal: Money,
    val taxableBaseTotal: Money,
    val taxComponents: List<TaxComponentAmount>,
    val taxTotal: Money,
    val chargesTotal: Money,
    val invoiceDiscountAmount: Money,
    val roundOffAmount: Money,
    val grandTotal: Money,
)

object DocumentTotalsCalculator {

    private val MONEY_SCALE = 2

    fun totalLine(input: DocumentLineInput): DocumentLineTotals {
        val gross = rounded(input.rate * input.quantity.amount)
        val discount = discountAmount(input.discount, gross)
        val discounted = gross - discount
        val tax = input.taxScheme?.let { TaxCalculator.calculate(discounted, it) }
        return DocumentLineTotals(
            grossAmount = gross,
            discountAmount = discount,
            taxableBase = tax?.taxableBase ?: discounted,
            taxComponents = tax?.components ?: emptyList(),
            totalTax = tax?.totalTax ?: Money.zero(gross.currency),
            lineTotal = tax?.grossAmount ?: discounted,
        )
    }

    fun totalCharge(input: AdditionalChargeInput): ChargeTotals {
        val tax = input.taxScheme?.let { TaxCalculator.calculate(input.amount, it) }
        return ChargeTotals(
            name = input.name,
            taxableBase = tax?.taxableBase ?: input.amount,
            taxComponents = tax?.components ?: emptyList(),
            totalTax = tax?.totalTax ?: Money.zero(input.amount.currency),
            grossAmount = tax?.grossAmount ?: input.amount,
        )
    }

    fun totalDocument(
        lines: List<DocumentLineInput>,
        charges: List<AdditionalChargeInput> = emptyList(),
        invoiceDiscount: Discount? = null,
        roundOff: Boolean = false,
        currencyHint: Money? = null,
    ): DocumentTotals {
        val lineTotals = lines.map(::totalLine)
        val chargeTotals = charges.map(::totalCharge)
        val currency = currencyHint
            ?: lineTotals.firstOrNull()?.grossAmount
            ?: chargeTotals.firstOrNull()?.grossAmount
        val zero = currency?.let { Money.zero(it.currency) }
            ?: throw IllegalArgumentException("A document with no lines and no charges has no currency; pass currencyHint")

        val grossBeforeDiscounts = lineTotals.fold(zero) { acc, line -> acc + line.grossAmount }
        val lineDiscountsTotal = lineTotals.fold(zero) { acc, line -> acc + line.discountAmount }
        val linesTotal = lineTotals.fold(zero) { acc, line -> acc + line.lineTotal }
        val chargesTotal = chargeTotals.fold(zero) { acc, charge -> acc + charge.grossAmount }
        val taxableBaseTotal =
            lineTotals.fold(zero) { acc, line -> acc + line.taxableBase } +
                chargeTotals.fold(zero) { acc, charge -> acc + charge.taxableBase }
        val taxTotal =
            lineTotals.fold(zero) { acc, line -> acc + line.totalTax } +
                chargeTotals.fold(zero) { acc, charge -> acc + charge.totalTax }
        val taxComponents = aggregateComponents(
            lineTotals.flatMap { it.taxComponents } + chargeTotals.flatMap { it.taxComponents }
        )

        val invoiceDiscountAmount = discountAmount(invoiceDiscount, linesTotal)
        val preRound = linesTotal - invoiceDiscountAmount + chargesTotal
        val grandTotal = if (roundOff) preRound.rounded(0, RoundingMode.HALF_UP) else preRound

        return DocumentTotals(
            lines = lineTotals,
            charges = chargeTotals,
            grossBeforeDiscounts = grossBeforeDiscounts,
            lineDiscountsTotal = lineDiscountsTotal,
            taxableBaseTotal = taxableBaseTotal,
            taxComponents = taxComponents,
            taxTotal = taxTotal,
            chargesTotal = chargesTotal,
            invoiceDiscountAmount = invoiceDiscountAmount,
            roundOffAmount = grandTotal - preRound,
            grandTotal = grandTotal,
        )
    }

    /** A discount against [base], clamped so the result never goes negative. */
    private fun discountAmount(discount: Discount?, base: Money): Money {
        val raw = when (discount) {
            null -> Money.zero(base.currency)
            is Discount.Percent -> rounded(discount.rate.applyTo(base))
            is Discount.Fixed -> discount.amount
        }
        return if (raw > base) base else raw
    }

    /** Merge per-component tax across lines and charges, keyed by component name + rate. */
    private fun aggregateComponents(parts: List<TaxComponentAmount>): List<TaxComponentAmount> {
        val byComponent = LinkedHashMap<TaxComponent, Money>()
        for (part in parts) {
            byComponent[part.component] = (byComponent[part.component] ?: Money.zero(part.amount.currency)) + part.amount
        }
        return byComponent.map { (component, amount) -> TaxComponentAmount(component, amount) }
    }

    private fun rounded(money: Money): Money = money.rounded(MONEY_SCALE, RoundingMode.HALF_UP)
}
