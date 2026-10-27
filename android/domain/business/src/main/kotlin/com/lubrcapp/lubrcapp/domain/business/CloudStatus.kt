package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 30.7 — cloud status surface. This
 * does NOT duplicate feature/syncstatus:
 * that module owns pairing (paired,
 * endpoint, error) and its screen. This
 * model answers the Phase 30 question the
 * pairing screen does not: given the
 * storage mode, the pairing state and the
 * tenant scope (30.1), which cloud
 * surfaces — portal (30.3), store (30.4),
 * admin (30.5) — are actually available?
 * The mode is local to this model because
 * domain/business cannot depend on
 * core/sync's StorageMode; the app shell
 * maps one to the other when it builds
 * this status.
 */
enum class CloudMode {
    LOCAL_ONLY,
    CLOUD_CONNECTED,
}

enum class CloudSurface {
    PORTAL,
    STORE,
    ADMIN,
}

data class CloudStatus(
    val mode: CloudMode,
    val paired: Boolean = false,
    val tenant: TenantScope? = null,
    val surfaces: Set<CloudSurface> = emptySet(),
)

object CloudStatusOps {
    fun isCloudReady(status: CloudStatus): Boolean =
        status.mode == CloudMode.CLOUD_CONNECTED && status.paired && status.tenant != null

    /** Surfaces are available only when the cloud is actually ready. */
    fun availableSurfaces(status: CloudStatus): Set<CloudSurface> =
        if (isCloudReady(status)) status.surfaces else emptySet()

    fun summary(status: CloudStatus): String {
        if (!isCloudReady(status)) return "Local only"
        val names = availableSurfaces(status).sortedBy { it.ordinal }.joinToString(", ") { it.name }
        return if (names.isEmpty()) "Cloud connected" else "Cloud connected: $names"
    }
}
