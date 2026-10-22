package com.lubrcapp.lubrcapp

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.AccessControlRepository
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Live capability gating for the app shell (Phase 13.3).
 *
 * The granted capabilities come from the production access-control path —
 * [AccessControlRepository], Hilt-bound to `RoomAccessControlRepository`
 * in core:store and already consumed by the settings access-control
 * screen through `AuthorizationService`. Nothing here re-implements
 * authorization: the repository resolves the current user's active
 * membership and role permissions for the business in [TenantContext],
 * and a user without an active membership resolves to its denied
 * snapshot (no membership, no capabilities).
 *
 * Reading follows the `AuthorizationService` precedent: the owner-access
 * repair runs first (it is idempotent and seeds the owner's full grants),
 * then the authorization snapshot is observed, so role or membership
 * changes for the same business re-gate the shell live.
 *
 * Failure semantics are deny-by-default, matching the access-control
 * contract (`AuthorizationSnapshot.allows` is false without an active
 * membership, and the settings screen treats a missing snapshot as not
 * allowed): a snapshot without an active membership emits the empty set,
 * and any resolution failure emits the empty set. Capability gating
 * therefore fails CLOSED — the deliberate opposite of Phase 13.2's
 * configuration flow, which fails open because configuration is a
 * white-label concern, not an authorization boundary.
 */
@HiltViewModel
class ShellAccessViewModel @Inject constructor(
    private val accessControlRepository: AccessControlRepository,
) : ViewModel() {

    fun grantedCapabilities(context: TenantContext): Flow<Set<CapabilityKey>> =
        flow {
            runCatching {
                accessControlRepository.ensureOwnerAccess(
                    context.tenantId,
                    context.businessId,
                )
            }
            emitAll(
                accessControlRepository.observeAuthorization(context).map { snapshot ->
                    if (snapshot.membershipId == null) {
                        emptySet()
                    } else {
                        snapshot.capabilities
                    }
                },
            )
        }.catch { emit(emptySet()) }
}
