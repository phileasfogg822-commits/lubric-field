package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) {
    suspend fun <T> run(block: suspend AppDatabase.() -> T): T =
        database.withTransaction {
            database.block()
        }
}
