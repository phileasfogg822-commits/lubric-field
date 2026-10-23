package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 16.2 — a warehouse / store location (master spec §34),
 * stored as its own operational record type ("warehouses") in the
 * same scoped store as items. The table name is the one the
 * Phase 12 sync store already whitelists.
 *
 * A warehouse optionally belongs to a [Branch] via [branchId]; a
 * null branch is a standalone location (the Main Shop pattern).
 * This package stores the master data only: no stock lives here —
 * quantities belong to the Phase 19 ledger, and the §35 transfer
 * workflow moves stock between warehouses there.
 *
 * Deleting a warehouse edits nothing else; deleting its branch
 * leaves the link in place, rendered unresolved ("—") — the
 * standing 14.3 orphan policy.
 */
data class Warehouse(
    val id: String,
    val name: String,
    val branchId: String? = null,
)
