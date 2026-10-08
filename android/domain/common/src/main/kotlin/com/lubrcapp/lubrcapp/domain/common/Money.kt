package com.lubrcapp.lubrcapp.domain.common

import java.math.BigDecimal
import java.math.RoundingMode

class Money private constructor(
    val amount: BigDecimal,
    val currency: CurrencyCode,
) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return of(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return of(amount.subtract(other.amount), currency)
    }

    operator fun unaryMinus(): Money = of(amount.negate(), currency)

    operator fun times(multiplier: BigDecimal): Money =
        of(amount.multiply(multiplier), currency)

    fun rounded(scale: Int, roundingMode: RoundingMode): Money {
        require(scale >= 0) { "Money scale cannot be negative" }
        return of(amount.setScale(scale, roundingMode), currency)
    }

    val isZero: Boolean get() = amount.signum() == 0
    val isNegative: Boolean get() = amount.signum() < 0
    val isPositive: Boolean get() = amount.signum() > 0

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: $currency vs ${other.currency}"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Money &&
            currency == other.currency &&
            amount.compareTo(other.amount) == 0

    override fun hashCode(): Int =
        31 * normalized(amount).hashCode() + currency.hashCode()

    override fun toString(): String = "${amount.toPlainString()} $currency"

    companion object {
        fun of(amount: BigDecimal, currency: CurrencyCode): Money =
            Money(normalized(amount), currency)

        fun of(amount: String, currency: CurrencyCode): Money =
            of(BigDecimal(amount), currency)

        fun zero(currency: CurrencyCode): Money =
            Money(BigDecimal.ZERO, currency)

        private fun normalized(value: BigDecimal): BigDecimal =
            if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()
    }
}
