package com.lubrcapp.lubrcapp.feature.printing

/**
 * Phase 25.5 — print settings: the paper and
 * finishing choices a printer-settings screen
 * (spec screen 67) will edit and the renderers
 * consume. Pure data, no storage: persistence
 * rides the app's usual record surface when the
 * settings screen lands, not this layer.
 *
 * PaperWidth maps to the thermal column count
 * the receipt renderer truncates to: 58 mm paper
 * carries 32 columns, 80 mm carries 42 — the
 * conventional defaults, and the 80 mm value
 * keeps 25.4's default. autoCut selects the
 * trailing GS V 0 cut on thermal receipts.
 */
enum class PaperWidth(val widthChars: Int) {
    MM_58(32),
    MM_80(42),
}

data class PrintSettings(
    val paperWidth: PaperWidth = PaperWidth.MM_80,
    val autoCut: Boolean = true,
)
