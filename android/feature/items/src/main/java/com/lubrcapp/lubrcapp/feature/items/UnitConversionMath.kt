package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Phase 16.1 — the pure unit-conversion rule (master spec §25).
 *
 * One hop only, by design: a direct record converts by
 * multiplication, the same record read backwards converts by
 * division, and anything else — no record, a non-positive factor,
 * or a path that would need chaining through a third unit —
 * returns null. A missing conversion is never guessed or chained:
 * callers show "no conversion" rather than a fabricated quantity.
 *
 * Division rounds at 10 decimal places (HALF_UP) and strips
 * trailing zeros, so 24 Bottles ÷ 12 comes back as exactly 2
 * Cartons while 1 Bottle ÷ 12 stays a precise decimal.
 */
object UnitConversionMath {

    fun convert(
        amount: BigDecimal,
        fromUnitId: String,
        toUnitId: String,
        conversions: List<UnitConversion>,
    ): BigDecimal? {
        if (fromUnitId == toUnitId) return amount
        val direct = conversions.firstOrNull {
            it.fromUnitId == fromUnitId && it.toUnitId == toUnitId && it.factor.signum() > 0
        }
        if (direct != null) return amount.multiply(direct.factor)
        val inverse = conversions.firstOrNull {
            it.fromUnitId == toUnitId && it.toUnitId == fromUnitId && it.factor.signum() > 0
        }
        if (inverse != null) {
            return amount.divide(inverse.factor, 10, RoundingMode.HALF_UP).stripTrailingZeros()
        }
        return null
    }
}
