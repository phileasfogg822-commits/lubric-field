package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val testDatabaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * Version 1 is the initial schema. This verifies the migration-test harness
     * can create it. When version 2 is introduced, add a test that creates v1,
     * writes representative rows, applies DatabaseMigrations.ALL, and validates
     * both schema and retained data.
     */
    @Test
    fun canCreateInitialSchemaForFutureMigrationTests() {
        helper.createDatabase(testDatabaseName, 1).close()
    }

    @Test
    fun migrationFromV1CreatesBusinessReplicaWithoutDroppingOldTables() {
        helper.createDatabase(testDatabaseName, 1).close()
        helper.runMigrationsAndValidate(
            testDatabaseName, 2, true, *DatabaseMigrations.ALL
        ).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='vps_business_records'").use {
                org.junit.Assert.assertTrue(it.moveToFirst())
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='settings'").use {
                org.junit.Assert.assertTrue(it.moveToFirst())
            }
        }
    }
}
