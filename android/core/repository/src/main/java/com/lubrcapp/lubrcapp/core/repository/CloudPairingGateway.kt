package com.lubrcapp.lubrcapp.core.repository

/**
 * Device pairing for opt-in cloud sync (Phase 12.3).
 *
 * Pairing stores the device token in the Android Keystore-backed credential
 * store and schedules authenticated sync for exactly one operational scope.
 * Unpairing clears the credential and cancels scheduled sync; local business
 * records are never touched by pairing or unpairing.
 */
interface CloudPairingGateway {
    /** True only when a token is stored and the pairing record matches [scope]. */
    suspend fun isPaired(scope: OperationalScope): Boolean

    /** The paired VPS endpoint when the pairing record matches [scope], else null. */
    fun pairedEndpoint(scope: OperationalScope): String?

    /**
     * Pairs this device for [scope]. The endpoint must use HTTPS and the
     * token must be non-blank; invalid input is rejected before any state
     * changes. Pairing a different scope replaces the previous pairing.
     */
    suspend fun pair(scope: OperationalScope, endpoint: String, token: String)

    /** Clears the stored credential and cancels scheduled sync. */
    suspend fun unpair(scope: OperationalScope)
}
