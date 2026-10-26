package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 28.4 — backup snapshot (§124–§126). Pure
 * snapshot model: a manifest plus the tables it
 * covers. The manifest is what the restore wizard
 * (§127) shows — backup date, business name,
 * record count per table — and what 28.5 verifies
 * before any restore.
 *
 * Deliberate non-inventions, per the prep pins:
 * - No encryption here: §126 says backups "should
 *   be encrypted", but key management is an owner
 *   decision, and an invented cipher would be worse
 *   than an honest gap. The snapshot carries data
 *   as-is; encryption lands when a key decision does.
 * - No cloud scheduling here: §124–§125 name
 *   automatic cloud backup, but no cloud endpoint
 *   exists in this tree, so this package covers
 *   the local snapshot; cloud is a named carryover.
 * - The snapshot is format-agnostic: tables are
 *   maps of field name to string value, so the
 *   writer (file, share, cloud) is a later layer's
 *   job, not this model's.
 *
 * Rules: [create] computes manifest counts from
 * the tables, so a created snapshot always
 * verifies. [verify] lists every inconsistency —
 * blank business name, blank date, a manifest
 * count that disagrees with its table's rows, a
 * manifest table missing from the snapshot, or a
 * table missing from the manifest — and an empty
 * list means valid. The file name follows §126:
 * `business_<ISO date>.busbackup`; a date that is
 * not ISO-shaped is rejected rather than baked
 * into a filename.
 */
data class BackupManifest(
    val backupDate: String,
    val businessName: String,
    val recordCounts: Map<String, Int>,
)

data class BackupSnapshot(
    val manifest: BackupManifest,
    val tables: Map<String, List<Map<String, String>>>,
)

object BackupSnapshotOps {
    private val isoDate = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun fileName(backupDate: String): String {
        require(isoDate.matches(backupDate)) {
            "Backup date must be ISO yyyy-MM-dd: $backupDate"
        }
        return "business_$backupDate.busbackup"
    }

    fun create(
        backupDate: String,
        businessName: String,
        tables: Map<String, List<Map<String, String>>>,
    ): BackupSnapshot {
        val counts = tables.mapValues { (_, rows) -> rows.size }
        return BackupSnapshot(BackupManifest(backupDate, businessName, counts), tables)
    }

    fun verify(snapshot: BackupSnapshot): List<String> {
        val problems = mutableListOf<String>()
        if (snapshot.manifest.backupDate.isBlank()) problems.add("Backup date is blank")
        if (snapshot.manifest.businessName.isBlank()) problems.add("Business name is blank")
        snapshot.manifest.recordCounts.forEach { (table, count) ->
            val rows = snapshot.tables[table]
            if (rows == null) {
                problems.add("Manifest table missing from snapshot: $table")
            } else if (rows.size != count) {
                problems.add("Count mismatch for $table: manifest $count, rows ${rows.size}")
            }
        }
        snapshot.tables.keys.forEach { table ->
            if (!snapshot.manifest.recordCounts.containsKey(table)) {
                problems.add("Table missing from manifest: $table")
            }
        }
        return problems
    }
}
