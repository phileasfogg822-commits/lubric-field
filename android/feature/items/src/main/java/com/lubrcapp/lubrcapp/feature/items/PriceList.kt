package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 16.3 — a named price list (master spec §26), stored as
 * its own operational record type ("price_lists") in the same
 * scoped store as items. The table name is the one the Phase 12
 * sync store already whitelists.
 *
 * A price list is just a name here; its per-item rates are
 * [PriceListItem] records. §26's customer-specific rates are §27
 * and still need sales history (Phases 18/21) — party and group
 * `priceLevel` strings stay free text in this phase and are NOT
 * silently bound to these entities.
 */
data class PriceList(
    val id: String,
    val name: String,
)
