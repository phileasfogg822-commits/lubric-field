package com.lubrcapp.lubrcapp.domain.common

import java.math.BigDecimal

class Percentage private constructor(
    val points: BigDecimal,
) : Comparable<Percentage> {

    init {
        require(points.signum() >= 0) { "Percentage cannot be negative" }
    }

    fun asFraction(): BigDecimal = points.movePointLeft(2)

    fun applyTo(money: Money): Money = money * asFraction()

    override fun compareTo(other: Percentage): Int = points.compareTo(other.points)

    override fun equals(other: Any?): Boolean =
        other is Percentage && points.compareTo(other.points) == 0

    override fun hashCode(): Int = normalized(points).hashCode()

    override fun toString(): String = "${points.toPlainString()}%"

    companion object {
        val ZERO: Percentage = Percentage(BigDecimal.ZERO)

        fun of(points: BigDecimal): Percentage = Percentage(normalized(points))
        fun of(points: String): Percentage = of(BigDecimal(points))

        private fun normalized(value: BigDecimal): BigDecimal =
            if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()
    }
}
