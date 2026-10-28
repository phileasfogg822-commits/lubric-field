package com.lubrcapp.lubrcapp.domain.business

import java.time.LocalDate

/**
 * Phase 30.6 — cross-device numbering. This
 * resolves the deferred Phase 17/18 item by
 * construction: the device id becomes the
 * branch prefix of the existing
 * DocumentNumbering format, so two devices
 * can never hand out the same formatted
 * number for the same document type, even
 * with independent local counters. Merging
 * is therefore a collapse of same-device
 * state, not a conflict resolution.
 *
 * ASSUMPTION: device ids are unique per
 * device (DevicePairingManager owns that).
 * If two devices ever share an id, this
 * construction no longer guarantees
 * uniqueness — server-side coordination
 * remains a backend follow-up.
 */
data class DeviceNumberingState(
    val deviceId: String,
    val periodKey: String = "",
    val lastIssued: Long = 0,
) {
    init {
        require(deviceId.isNotBlank()) { "Device id is required" }
    }
}

object DeviceNumbering {

    fun allocate(
        sequence: NumberingSequence,
        state: DeviceNumberingState,
        date: LocalDate,
    ): Pair<DeviceNumberingState, String> {
        val (advanced, number) = DocumentNumbering.issue(
            sequence,
            NumberingState(state.periodKey, state.lastIssued),
            date,
            branchPrefix = state.deviceId,
        )
        return DeviceNumberingState(state.deviceId, advanced.periodKey, advanced.lastIssued) to number
    }

    /** Same device + same period collapse to the max; other devices survive. */
    fun merge(states: List<DeviceNumberingState>): List<DeviceNumberingState> =
        states
            .groupBy { it.deviceId to it.periodKey }
            .map { (key, group) ->
                DeviceNumberingState(key.first, key.second, group.maxOf { it.lastIssued })
            }
            .sortedWith(compareBy({ it.deviceId }, { it.periodKey }))

    /** A duplicate formatted number means one device issued twice — a bug, not a merge. */
    fun hasCollision(numbers: List<String>): Boolean =
        numbers.size != numbers.toSet().size
}
