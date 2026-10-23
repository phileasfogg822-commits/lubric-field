package com.lubrcapp.lubrcapp.feature.items

/**
 * Phase 15.3 — an item category (master spec §23), stored as its own
 * operational record type ("categories") in the same scoped store as
 * items.
 *
 * §23 asks for a hierarchy with unlimited nesting preferred: a
 * category links to its parent by [parentId]; a null parent is a
 * top-level category. Items link by [Item.categoryId]; subcategories
 * link the same way.
 *
 * Deleting a category never deletes or edits its items or
 * subcategories: their links are kept and the UI shows them
 * unresolved ("—"), because sync may resurrect the category from
 * another device — the same orphan policy as party groups in 14.3.
 */
data class ItemCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
)
