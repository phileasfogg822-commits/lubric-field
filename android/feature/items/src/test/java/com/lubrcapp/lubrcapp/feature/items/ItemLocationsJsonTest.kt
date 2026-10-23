package com.lubrcapp.lubrcapp.feature.items

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.2: branch and warehouse codecs round-trip and tolerate legacy payloads. */
class ItemLocationsJsonTest {

    @Test fun branchRoundTrip() {
        val branch = Branch(id = "br-1", name = "Main Shop")
        assertEquals(branch, BranchJson.decode("br-1", BranchJson.encode(branch)))
    }

    @Test fun branchDecodeToleratesLegacyAndBadPayloads() {
        assertEquals("Legacy", BranchJson.decode("br-7", """{"uuid":"x","name":"Legacy"}""").name)
        assertEquals("br-7", BranchJson.decode("br-7", """{"name":"Legacy"}""").id)
        assertEquals("", BranchJson.decode("br-8", "not json").name)
        assertEquals("", BranchJson.decode("br-9", "").name)
    }

    @Test fun warehouseRoundTripWithAndWithoutBranch() {
        val linked = Warehouse(id = "w-1", name = "Godown", branchId = "br-1")
        assertEquals(linked, WarehouseJson.decode("w-1", WarehouseJson.encode(linked)))
        val standalone = Warehouse(id = "w-2", name = "Main Shop Store")
        assertEquals(standalone, WarehouseJson.decode("w-2", WarehouseJson.encode(standalone)))
    }

    @Test fun warehouseDecodeToleratesLegacyAndBadPayloads() {
        val legacy = WarehouseJson.decode("w-7", """{"uuid":"x","name":"Old Godown"}""")
        assertEquals("Old Godown", legacy.name)
        assertEquals(null, legacy.branchId)
        assertEquals("", WarehouseJson.decode("w-8", "junk").name)
    }
}
