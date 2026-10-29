package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.isDemoScopeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCompaniesTest {

    @Test
    fun demoPrefixIdentifiesDemoScopesOnly() {
        assertTrue(isDemoScopeId("demo-business-store"))
        assertTrue(isDemoScopeId("demo-tenant-store"))
        assertFalse(isDemoScopeId("business-a"))
        assertFalse(isDemoScopeId("demo"))
        assertFalse(isDemoScopeId(""))
    }

    @Test
    fun demoCompaniesAreTwoDistinctDemoScopes() {
        assertEquals(2, DEMO_COMPANIES.size)
        DEMO_COMPANIES.forEach { def ->
            assertTrue(isDemoScopeId(def.tenantId))
            assertTrue(isDemoScopeId(def.businessId))
            assertTrue(def.name.isNotBlank())
        }
        assertEquals(2, DEMO_COMPANIES.map { it.tenantId }.toSet().size)
        assertEquals(2, DEMO_COMPANIES.map { it.businessId }.toSet().size)
    }
}
