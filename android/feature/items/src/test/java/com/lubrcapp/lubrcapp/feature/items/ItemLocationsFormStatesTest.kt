package com.lubrcapp.lubrcapp.feature.items

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.2: branch/warehouse form states trim and carry the branch link. */
class ItemLocationsFormStatesTest {

    @Test fun branchFormTrimsName() {
        val form = BranchFormState()
        form.name = "  Main Shop  "
        assertEquals(Branch("br-1", "Main Shop"), form.toBranch("br-1"))
    }

    @Test fun warehouseFormCarriesBranchLink() {
        val form = WarehouseFormState()
        form.name = " Godown "
        form.branchId = "br-1"
        assertEquals(Warehouse("w-1", "Godown", "br-1"), form.toWarehouse("w-1"))
    }

    @Test fun warehouseFormDefaultsToStandalone() {
        val form = WarehouseFormState()
        form.name = "Store"
        assertEquals(null, form.toWarehouse("w-2").branchId)
        form.load(Warehouse("w-3", "Depot", "br-9"))
        assertEquals("br-9", form.branchId)
        form.reset()
        assertEquals("", form.name)
        assertEquals(null, form.branchId)
    }
}
