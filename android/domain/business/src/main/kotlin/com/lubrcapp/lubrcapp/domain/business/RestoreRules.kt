package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 28.5 — restore rules (§127). The restore
 * wizard is Select File, Verify, Show Backup Date,
 * Show Business Name, Show Record Count, Restore —
 * and never overwrite silently.
 *
 * This package is the rule half of that wizard:
 * [RestoreRules.preview] turns a snapshot into
 * the exact screen the wizard shows (date, business
 * name, record counts, plus every verification
 * problem from 28.4's verify), [decide] says
 * whether the snapshot is restorable at all, and
 * [canRestore] is the final gate: a snapshot
 * restores only when it is valid AND the user
 * explicitly confirmed the overwrite. A valid
 * snapshot without confirmation does not restore;
 * an invalid snapshot with confirmation does not
 * restore either — neither half alone is enough,
 * which is what "never overwrite silently" means
 * as a rule.
 *
 * No actual restore writes happen here: applying
 * a snapshot's tables through the repository is a
 * shell-layer job that needs the whitelist and
 * posting rules, not a pure decision.
 */
data class RestorePreview(
    val backupDate: String,
    val businessName: String,
    val recordCounts: Map<String, Int>,
    val problems: List<String>,
) {
    val totalRecords: Int get() = recordCounts.values.sum()
}

sealed interface RestoreDecision {
    data object Ready : RestoreDecision
    data class Blocked(val problems: List<String>) : RestoreDecision
}

object RestoreRules {
    fun preview(snapshot: BackupSnapshot): RestorePreview {
        val manifest = snapshot.manifest
        return RestorePreview(
            backupDate = manifest.backupDate,
            businessName = manifest.businessName,
            recordCounts = manifest.recordCounts,
            problems = BackupSnapshotOps.verify(snapshot),
        )
    }

    fun decide(preview: RestorePreview): RestoreDecision {
        return if (preview.problems.isEmpty()) {
            RestoreDecision.Ready
        } else {
            RestoreDecision.Blocked(preview.problems)
        }
    }

    fun canRestore(preview: RestorePreview, confirmed: Boolean): Boolean {
        return decide(preview) is RestoreDecision.Ready && confirmed
    }
}
