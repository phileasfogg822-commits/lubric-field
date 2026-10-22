package com.lubrcapp.lubrcapp

import com.lubrcapp.lubrcapp.core.repository.AccessControlRepository
import com.lubrcapp.lubrcapp.core.repository.AuthorizationSnapshot
import com.lubrcapp.lubrcapp.core.repository.BusinessMember
import com.lubrcapp.lubrcapp.core.repository.BusinessRole
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessRoleRequest
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessUserRequest
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.shell.AppModuleRegistry
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Shell capability source (Phase 13.3): the view model is a thin,
 * fail-closed window on the production [AccessControlRepository]. It
 * must forward the granted capability set for exactly the business the
 * shell asks about, pass live authorization updates through, and degrade
 * to the empty set — never to "no gating" — when the user has no active
 * membership or resolution fails, because access control is
 * deny-by-default (`AuthorizationSnapshot.allows`).
 */
class ShellAccessViewModelTest {

    private class FakeAccessControlRepository(
        private val snapshots: (TenantContext) -> Flow<AuthorizationSnapshot>,
    ) : AccessControlRepository {
        val observedContexts = mutableListOf<TenantContext>()
        val ownerAccessEnsuredFor = mutableListOf<Pair<TenantId, BusinessId>>()

        override fun observeAuthorization(
            context: TenantContext,
        ): Flow<AuthorizationSnapshot> {
            observedContexts += context
            return snapshots(context)
        }

        override suspend fun authorization(
            context: TenantContext,
        ): AuthorizationSnapshot = snapshots(context).first()

        override suspend fun ensureOwnerAccess(
            tenantId: TenantId,
            businessId: BusinessId,
        ) {
            ownerAccessEnsuredFor += tenantId to businessId
        }

        override fun observeMembers(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Flow<List<BusinessMember>> = flowOf(emptyList())

        override fun observeRoles(
            tenantId: TenantId,
            businessId: BusinessId,
        ): Flow<List<BusinessRole>> = flowOf(emptyList())

        override suspend fun createBusinessUser(
            actor: TenantContext,
            request: CreateBusinessUserRequest,
        ): BusinessMember = error("not used in shell access tests")

        override suspend fun setMembershipActive(
            actor: TenantContext,
            membershipId: UserBusinessId,
            active: Boolean,
        ): Unit = error("not used in shell access tests")

        override suspend fun createRole(
            actor: TenantContext,
            request: CreateBusinessRoleRequest,
        ): BusinessRole = error("not used in shell access tests")

        override suspend fun setRoleActive(
            actor: TenantContext,
            roleId: RoleId,
            active: Boolean,
        ): Unit = error("not used in shell access tests")

        override suspend fun setRoleCapabilities(
            actor: TenantContext,
            roleId: RoleId,
            capabilities: Set<CapabilityKey>,
        ): Unit = error("not used in shell access tests")

        override suspend fun assignRoles(
            actor: TenantContext,
            membershipId: UserBusinessId,
            roleIds: Set<RoleId>,
        ): Unit = error("not used in shell access tests")
    }

    private fun context(tenant: String, business: String) = TenantContext(
        tenantId = TenantId(tenant),
        businessId = BusinessId(business),
        userId = UserId("user-1"),
    )

    private fun snapshot(
        context: TenantContext,
        member: Boolean,
        capabilities: Set<CapabilityKey>,
    ) = AuthorizationSnapshot(
        context = context,
        membershipId = if (member) UserBusinessId("membership-1") else null,
        roleIds = if (member) setOf(RoleId("role-1")) else emptySet(),
        capabilities = capabilities,
    )

    @Test
    fun forwardsTheGrantsForTheRequestedContext() = runBlocking {
        val repository = FakeAccessControlRepository { context ->
            flowOf(
                when (context.businessId) {
                    BusinessId("business-a") -> snapshot(
                        context,
                        member = true,
                        capabilities = setOf(Capabilities.ITEMS_VIEW, Capabilities.SALES_VIEW),
                    )
                    else -> snapshot(
                        context,
                        member = true,
                        capabilities = setOf(Capabilities.SYNC_MANAGE),
                    )
                },
            )
        }
        val viewModel = ShellAccessViewModel(repository)

        assertEquals(
            setOf(Capabilities.ITEMS_VIEW, Capabilities.SALES_VIEW),
            viewModel.grantedCapabilities(context("tenant-a", "business-a")).first(),
        )
        // A different business is a different membership: the shell
        // re-queries per selected business instead of reusing stale grants.
        assertEquals(
            setOf(Capabilities.SYNC_MANAGE),
            viewModel.grantedCapabilities(context("tenant-a", "business-b")).first(),
        )
        assertEquals(
            listOf(
                TenantId("tenant-a") to BusinessId("business-a"),
                TenantId("tenant-a") to BusinessId("business-b"),
            ),
            repository.ownerAccessEnsuredFor,
        )
        assertEquals(
            listOf(
                context("tenant-a", "business-a"),
                context("tenant-a", "business-b"),
            ),
            repository.observedContexts,
        )
    }

    @Test
    fun snapshotWithoutMembershipYieldsNoGrants() = runBlocking {
        // The repository's denied snapshot carries no capabilities, but
        // the guard is the membership itself: even a snapshot that named
        // capabilities without an active membership grants nothing,
        // exactly like AuthorizationSnapshot.allows.
        val viewModel = ShellAccessViewModel(
            FakeAccessControlRepository { context ->
                flowOf(
                    snapshot(
                        context,
                        member = false,
                        capabilities = setOf(Capabilities.SALES_VIEW),
                    ),
                )
            },
        )

        assertEquals(
            emptySet<CapabilityKey>(),
            viewModel.grantedCapabilities(context("tenant-a", "business-a")).first(),
        )
    }

    @Test
    fun liveAuthorizationUpdatesFlowThrough() = runBlocking {
        val viewModel = ShellAccessViewModel(
            FakeAccessControlRepository { context ->
                flowOf(
                    snapshot(context, member = true, setOf(Capabilities.ITEMS_VIEW)),
                    snapshot(
                        context,
                        member = true,
                        setOf(Capabilities.ITEMS_VIEW, Capabilities.SALES_VIEW),
                    ),
                )
            },
        )

        assertEquals(
            listOf(
                setOf(Capabilities.ITEMS_VIEW),
                setOf(Capabilities.ITEMS_VIEW, Capabilities.SALES_VIEW),
            ),
            viewModel.grantedCapabilities(context("tenant-a", "business-a")).toList(),
        )
    }

    @Test
    fun resolutionFailureFallsBackToDenied() = runBlocking {
        // Fail-closed, deliberately unlike the Phase 13.2 configuration
        // source: an authorization error hides capability-gated modules
        // instead of showing them.
        val viewModel = ShellAccessViewModel(
            FakeAccessControlRepository {
                flow<AuthorizationSnapshot> {
                    throw IllegalStateException("access state unreadable")
                }
            },
        )

        assertEquals(
            emptySet<CapabilityKey>(),
            viewModel.grantedCapabilities(context("tenant-a", "business-a")).first(),
        )
    }

    @Test
    fun grantsComposeWithTheRegistryCapabilityGate() = runBlocking {
        val viewModel = ShellAccessViewModel(
            FakeAccessControlRepository { context ->
                flowOf(
                    snapshot(
                        context,
                        member = true,
                        capabilities = setOf(
                            Capabilities.ITEMS_VIEW,
                            Capabilities.INVENTORY_VIEW,
                            Capabilities.PARTIES_VIEW,
                        ),
                    ),
                )
            },
        )

        val grants = viewModel.grantedCapabilities(context("tenant-a", "business-a")).first()

        // Configuration enables everything; the grants alone decide, and
        // only items, inventory and parties pass.
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
            ),
            AppModuleRegistry.visibleModules(FeatureFlagKeys.ALL, grants).map { it.id },
        )
    }
}
