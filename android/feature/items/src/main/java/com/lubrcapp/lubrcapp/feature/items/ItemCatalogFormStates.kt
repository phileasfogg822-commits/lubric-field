package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Phase 15.3 — editable form state for the category form (§23).
 * Same contract as [ItemFormState]: text-first fields, blank
 * optionals become null. A category can never be its own parent:
 * [toCategory] drops a self-reference rather than storing a cycle.
 */
class ItemCategoryFormState {
    var name by mutableStateOf("")
    var parentId by mutableStateOf<String?>(null)

    /** Starts a blank form (new category). */
    fun reset() {
        load(ItemCategory(id = "", name = ""))
    }

    /** Loads an existing category into the form for editing. */
    fun load(category: ItemCategory) {
        name = category.name
        parentId = category.parentId
    }

    /** The category to save under [id]; a self-parent link is dropped. */
    fun toCategory(id: String): ItemCategory = ItemCategory(
        id = id,
        name = name.trim(),
        parentId = parentId?.takeUnless { it == id },
    )
}

/**
 * Phase 15.3 — editable form state for the brand form (§24).
 * Name only: §24 defines no further brand fields in this phase.
 */
class BrandFormState {
    var name by mutableStateOf("")

    /** Starts a blank form (new brand). */
    fun reset() {
        load(Brand(id = "", name = ""))
    }

    /** Loads an existing brand into the form for editing. */
    fun load(brand: Brand) {
        name = brand.name
    }

    /** The brand to save under [id]. */
    fun toBrand(id: String): Brand = Brand(
        id = id,
        name = name.trim(),
    )
}
