package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.migration.Migration

/**
 * Central registry for every schema migration.
 *
 * Version 1 is the initial Room schema, so there is no historical migration yet.
 * Every future schema bump must add a concrete Migration here and a matching
 * MigrationTestHelper test before the database version is changed.
 */
object DatabaseMigrations {
    const val SCHEMA_VERSION: Int = 1

    val ALL: Array<Migration> = emptyArray()
}
