package com.lubrcapp.lubrcapp.domain.common

import java.math.BigDecimal
import java.math.RoundingMode

class Quantity private constructor(
    val amount: BigDecimal,
) : Comparable<Quantity> {

    operator fun plus(other: Quantity): Quantity = of(amount.add(other.amount))
    operator fun minus(other: Quantity): Quantity = of(amount.subtract(other.amount))
    operator fun unaryMinus(): Quantity = of(amount.negate())
    operator fun times(multiplier: BigDecimal): Quantity = of(amount.multiply(multiplier))

    fun rounded(scale: Int, roundingMode: RoundingMode): Quantity {
        require(scale >= 0) { "Quantity scale cannot be negative" }
        return of(amount.setScale(scale, roundingMode))
    }

    val isZero: Boolean get() = amount.signum() == 0
    val isNegative: Boolean get() = amount.signum() < 0
    val isPositive: Boolean get() = amount.signum() > 0

    override fun compareTo(other: Quantity): Int = amount.compareTo(other.amount)

    override fun equals(other: Any?): Boolean =
        other is Quantity && amount.compareTo(other.amount) == 0

    override fun hashCode(): Int = normalized(amount).hashCode()

    override fun toString(): String = amount.toPlainString()

    companion object {
        val ZERO: Quantity = Quantity(BigDecimal.ZERO)

        fun of(amount: BigDecimal): Quantity = Quantity(normalized(amount))
        fun of(amount: String): Quantity = of(BigDecimal(amount))

        private fun normalized(value: BigDecimal): BigDecimal =
            if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()
    }
}
