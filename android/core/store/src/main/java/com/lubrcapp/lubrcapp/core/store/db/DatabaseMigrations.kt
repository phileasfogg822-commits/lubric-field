package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    const val SCHEMA_VERSION: Int = 2

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `vps_business_records` (
                    `tenantId` TEXT NOT NULL,
                    `businessId` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`tenantId`, `businessId`, `entityType`, `entityId`)
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vps_business_records_tenantId_businessId_entityType` ON `vps_business_records` (`tenantId`, `businessId`, `entityType`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
