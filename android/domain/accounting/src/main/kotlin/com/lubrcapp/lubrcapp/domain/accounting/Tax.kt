package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage

/**
 * Generic tax model (master spec §8.2). A scheme is data, never code: no
 * tax name (GST, VAT, Sales Tax) is hard-coded anywhere in the engine, and
 * a scheme may carry any number of components (e.g. one 17% component, or
 * CGST 9% + SGST 9%), so new countries never require restructuring.
 */
data class TaxComponent(
    val name: String,
    val rate: Percentage,
) {
    init {
        require(name.isNotBlank()) { "Tax component name is required" }
    }
}

data class TaxScheme(
    val name: String,
    val registrationNumber: String? = null,
    val inclusive: Boolean,
    val components: List<TaxComponent> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Tax scheme name is required" }
    }

    /** Sum of all component rates, in percentage points. */
    val totalRate: Percentage
        get() = Percentage.of(
            components.fold(java.math.BigDecimal.ZERO) { acc, component ->
                acc.add(component.rate.points)
            }
        )
}

data class TaxComponentAmount(
    val component: TaxComponent,
    val amount: Money,
)

/**
 * The result of applying a [TaxScheme] to one taxable amount.
 *
 * [taxableBase] is the net amount the tax was computed on and
 * [grossAmount] is base + tax. For an exclusive scheme the entered amount
 * IS the base; for an inclusive scheme the entered amount IS the gross.
 * In both cases `grossAmount - taxableBase == totalTax` and the component
 * amounts sum exactly to [totalTax].
 */
data class TaxResult(
    val taxableBase: Money,
    val components: List<TaxComponentAmount>,
    val totalTax: Money,
    val grossAmount: Money,
)
