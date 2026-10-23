package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Phase 16.2 — editable form state for the branch form (§34).
 * Name only: §34 defines no further branch fields in this phase.
 */
class BranchFormState {
    var name by mutableStateOf("")

    /** Starts a blank form (new branch). */
    fun reset() {
        load(Branch(id = "", name = ""))
    }

    /** Loads an existing branch into the form for editing. */
    fun load(branch: Branch) {
        name = branch.name
    }

    /** The branch to save under [id]. */
    fun toBranch(id: String): Branch = Branch(
        id = id,
        name = name.trim(),
    )
}

/**
 * Phase 16.2 — editable form state for the warehouse form (§34).
 * Same contract as the other catalog forms: text-first, the
 * optional branch link is carried as an id (null = standalone).
 */
class WarehouseFormState {
    var name by mutableStateOf("")
    var branchId by mutableStateOf<String?>(null)

    /** Starts a blank form (new warehouse). */
    fun reset() {
        load(Warehouse(id = "", name = ""))
    }

    /** Loads an existing warehouse into the form for editing. */
    fun load(warehouse: Warehouse) {
        name = warehouse.name
        branchId = warehouse.branchId
    }

    /** The warehouse to save under [id]. */
    fun toWarehouse(id: String): Warehouse = Warehouse(
        id = id,
        name = name.trim(),
        branchId = branchId,
    )
}
