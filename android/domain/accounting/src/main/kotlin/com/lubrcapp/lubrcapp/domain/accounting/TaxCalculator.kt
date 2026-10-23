package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure tax computation for one taxable amount under one [TaxScheme].
 *
 * Rounding policy (explicit, pinned by tests): money is rounded to 2
 * decimal places with HALF_UP, per component. In inclusive mode the base
 * is rounded first and the total tax is the exact remainder, so base +
 * tax always equals the entered gross; component shares are rounded
 * individually and any residue is absorbed by the largest component, so
 * component amounts always sum exactly to the total tax.
 */
object TaxCalculator {

    private val MONEY_SCALE = 2
    private val WORKING_SCALE = 10

    fun calculate(amount: Money, scheme: TaxScheme): TaxResult =
        if (scheme.inclusive) inclusive(amount, scheme) else exclusive(amount, scheme)

    private fun exclusive(amount: Money, scheme: TaxScheme): TaxResult {
        val parts = scheme.components.map { component ->
            TaxComponentAmount(component, rounded(component.rate.applyTo(amount)))
        }
        val totalTax = parts.fold(Money.zero(amount.currency)) { acc, part -> acc + part.amount }
        return TaxResult(
            taxableBase = amount,
            components = parts,
            totalTax = totalTax,
            grossAmount = amount + totalTax,
        )
    }

    private fun inclusive(amount: Money, scheme: TaxScheme): TaxResult {
        val totalRate = scheme.totalRate
        if (scheme.components.isEmpty() || totalRate.points.signum() == 0) {
            return TaxResult(
                taxableBase = amount,
                components = scheme.components.map { TaxComponentAmount(it, Money.zero(amount.currency)) },
                totalTax = Money.zero(amount.currency),
                grossAmount = amount,
            )
        }
        val divisor = BigDecimal.ONE.add(totalRate.asFraction())
        val base = rounded(Money.of(amount.amount.divide(divisor, WORKING_SCALE, RoundingMode.HALF_UP), amount.currency))
        val totalTax = amount - base

        val rawShares = scheme.components.map { component ->
            totalTax.amount.multiply(component.rate.points)
                .divide(totalRate.points, WORKING_SCALE, RoundingMode.HALF_UP)
        }
        val roundedShares = rawShares.map { rounded(Money.of(it, amount.currency)) }.toMutableList()
        val residue = totalTax - roundedShares.fold(Money.zero(amount.currency)) { acc, share -> acc + share }
        if (!residue.isZero && roundedShares.isNotEmpty()) {
            val largest = rawShares.indices.maxByOrNull { rawShares[it].abs() } ?: 0
            roundedShares[largest] = roundedShares[largest] + residue
        }
        return TaxResult(
            taxableBase = base,
            components = scheme.components.mapIndexed { index, component ->
                TaxComponentAmount(component, roundedShares[index])
            },
            totalTax = totalTax,
            grossAmount = amount,
        )
    }

    private fun rounded(money: Money): Money = money.rounded(MONEY_SCALE, RoundingMode.HALF_UP)
}
