package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal

/**
 * Phase 16.1 — a conversion between two units (master spec §25),
 * stored as its own operational record type ("unit_conversions").
 *
 * [factor] states how many [toUnitId] units make one [fromUnitId]
 * unit: 1 Carton = 12 Bottles is fromUnitId = Carton,
 * toUnitId = Bottle, factor = 12. The reverse direction needs no
 * second record — [UnitConversionMath] divides by the same factor.
 *
 * A factor of zero is the codec's sentinel for a stored record
 * whose factor was missing or unreadable; the conversion rule
 * treats any non-positive factor as "no path" and never converts
 * with it. The form only ever saves factors greater than zero.
 */
data class UnitConversion(
    val id: String,
    val fromUnitId: String,
    val toUnitId: String,
    val factor: BigDecimal,
)
