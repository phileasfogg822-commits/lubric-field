package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 16.5 — barcode lookup and list search (master spec §28).
 *
 * The §28 "search" function needs no hardware: a barcode typed or
 * wedged into the search field (USB/Bluetooth HID scanners act as
 * keyboards, so their input lands in the field with no code)
 * matches an item by EXACT barcode — never a substring, so a
 * partial code can never surface the wrong product. A query that
 * is not an exact barcode falls back to a case-insensitive name
 * substring, which is what a human typing a name expects.
 *
 * Camera scanning needs a scanning library — a new dependency
 * and an owner decision — and is deliberately NOT part of this
 * package; nothing here pretends to scan.
 */
object ItemSearch {

    /** The item whose barcode exactly equals [code] (both trimmed), or null. */
    fun findByBarcode(items: List<Item>, code: String): Item? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        return items.firstOrNull { it.barcode?.trim() == trimmed }
    }

    /**
     * The list filter: a blank query returns every item; an exact
     * barcode hit returns just that item; otherwise items whose
     * name contains the query (case-insensitive).
     */
    fun filter(items: List<Item>, query: String): List<Item> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items
        findByBarcode(items, trimmed)?.let { return listOf(it) }
        return items.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
}
