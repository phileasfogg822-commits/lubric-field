package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * Phase 16.1 — editable form state for the unit form (§25).
 * Name only; the §25 suggested names are quick picks in the UI.
 */
class ItemUnitFormState {
    var name by mutableStateOf("")

    /** Starts a blank form (new unit). */
    fun reset() {
        load(ItemUnit(id = "", name = ""))
    }

    /** Loads an existing unit into the form for editing. */
    fun load(unit: ItemUnit) {
        name = unit.name
    }

    /** The unit to save under [id]. */
    fun toUnit(id: String): ItemUnit = ItemUnit(
        id = id,
        name = name.trim(),
    )
}

/**
 * Phase 16.1 — editable form state for the conversion form (§25).
 *
 * [toConversion] returns null whenever the conversion would be
 * meaningless: either side unpicked, both sides the same unit, or
 * a factor that is blank, non-numeric, or not greater than zero.
 * The screen keeps the save disabled in exactly those states and
 * shows the form error if it is ever reached.
 */
class UnitConversionFormState {
    var fromUnitId by mutableStateOf<String?>(null)
    var toUnitId by mutableStateOf<String?>(null)
    var factor by mutableStateOf("")

    /** Starts a blank form (new conversion). */
    fun reset() {
        load(UnitConversion(id = "", fromUnitId = "", toUnitId = "", factor = BigDecimal.ZERO))
    }

    /** Loads an existing conversion into the form for editing. */
    fun load(conversion: UnitConversion) {
        fromUnitId = conversion.fromUnitId.takeIf { it.isNotEmpty() }
        toUnitId = conversion.toUnitId.takeIf { it.isNotEmpty() }
        factor = if (conversion.factor.signum() > 0) conversion.factor.toPlainString() else ""
    }

    /** The conversion to save under [id], or null when invalid. */
    fun toConversion(id: String): UnitConversion? {
        val from = fromUnitId ?: return null
        val to = toUnitId ?: return null
        if (from == to) return null
        val parsed = factor.trim().toBigDecimalOrNull() ?: return null
        if (parsed.signum() <= 0) return null
        return UnitConversion(
            id = id,
            fromUnitId = from,
            toUnitId = to,
            factor = parsed,
        )
    }
}
