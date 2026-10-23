package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 16.1 — a unit of measure (master spec §25), stored as its
 * own operational record type ("units") in the same scoped store as
 * items.
 *
 * Named [ItemUnit] rather than Unit so it never shadows
 * `kotlin.Unit` inside this package. §25 names the common units —
 * Piece / Bottle / Liter / Carton / Box / Dozen / Kg / Meter — in
 * [SUGGESTED_UNIT_NAMES]; the list is a UI convenience for creating
 * units quickly, not an enum: a business can name any unit it
 * actually uses.
 *
 * Items link by [Item.unitId]; the older free-text [Item.unit] name
 * stays for records written before this entity existed. Deleting a
 * unit never edits the items or conversions that link to it: their
 * ids stay and render unresolved ("—"), because sync may resurrect
 * the unit — the standing 14.3 orphan policy.
 */
data class ItemUnit(
    val id: String,
    val name: String,
)

/** The §25 unit names, offered as quick picks when creating a unit. */
val SUGGESTED_UNIT_NAMES: List<String> = listOf(
    "Piece",
    "Bottle",
    "Liter",
    "Carton",
    "Box",
    "Dozen",
    "Kg",
    "Meter",
)
