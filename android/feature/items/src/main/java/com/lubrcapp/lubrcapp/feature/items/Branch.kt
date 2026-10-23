package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 16.2 — a branch of the business (master spec §34), stored
 * as its own operational record type ("branches") in the same
 * scoped store as items.
 *
 * §34's world is Main Shop / Godown / branch stores: a branch is
 * the business location a warehouse belongs to. This package
 * stores the master data only — warehouse-specific transactions
 * belong to the phases that own transactions, and the §35
 * stock-transfer workflow belongs to the Phase 19 ledger.
 *
 * Branches are hosted in the items feature beside the other
 * catalog master data (categories, brands, units) because they
 * share the same store, ViewModel and playbook; the settings
 * feature is preferences-only and the inventory feature's typed
 * surface arrives with Phase 19.
 *
 * Deleting a branch never edits its warehouses: their [Warehouse]
 * branch links stay and render unresolved ("—"), because sync may
 * resurrect the branch — the standing 14.3 orphan policy.
 */
data class Branch(
    val id: String,
    val name: String,
)
