package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Prevents existing audit events from being silently rewritten or removed.
 * New events remain append-only. Sync state is tracked in sync_queue rather
 * than by rewriting historical audit rows.
 *
 * This protects against application SQL mistakes, not a compromised device
 * owner who can replace the database or drop triggers.
 */
object AuditIntegrityGuard {
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS audit_logs_no_update
               BEFORE UPDATE ON audit_logs
               BEGIN SELECT RAISE(ABORT, 'audit history is immutable'); END"""
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS audit_logs_no_delete
               BEFORE DELETE ON audit_logs
               BEGIN SELECT RAISE(ABORT, 'audit history is immutable'); END"""
        )
    }

    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            install(db)
        }
    }
}
