package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 15.3 — a brand (master spec §24), stored as its own
 * operational record type ("brands") in the same scoped store as
 * items.
 *
 * §24 makes Brand a separate entity rather than a text field on the
 * item, so brand-level reporting can build on it later; this phase
 * stores the entity and the [Item.brandId] link only.
 *
 * Deleting a brand never deletes or edits its items: the link is
 * kept and the UI shows it unresolved ("—"), because sync may
 * resurrect the brand from another device — the same orphan policy
 * as party groups in 14.3.
 */
data class Brand(
    val id: String,
    val name: String,
)
