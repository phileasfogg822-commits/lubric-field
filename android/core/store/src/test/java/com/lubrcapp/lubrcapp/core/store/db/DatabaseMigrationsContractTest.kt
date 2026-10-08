package com.lubrcapp.lubrcapp.core.store.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Initial migration contract.
 *
 * Version 1 has no predecessor. On the first schema bump this test must be
 * expanded to assert a complete migration path from 1 to the current version,
 * and the instrumentation MigrationTestHelper suite must exercise that path.
 */
class DatabaseMigrationsContractTest {
    @Test
    fun initialSchemaVersionAndMigrationRegistryStayAligned() {
        assertEquals(1, DatabaseMigrations.SCHEMA_VERSION)
        assertEquals(0, DatabaseMigrations.ALL.size)
    }
}
