package com.lubrcapp.lubrcapp.core.repository

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorizationContractTest {

    @Test
    fun contractCarriesOnlyStableIdsAndCapabilityKeys() {
        val snapshot = AuthorizationSnapshot(
            context = TenantContext(
                tenantId = TenantId("tenant-1"),
                businessId = BusinessId("business-1"),
                userId = UserId("user-1"),
            ),
            membershipId = UserBusinessId("membership-1"),
            roleIds = setOf(RoleId("role-1")),
            capabilities = setOf(
                Capabilities.SALES_CREATE,
                Capabilities.PARTIES_VIEW,
            ),
        )

        val contract = snapshot.toContract()

        assertEquals("tenant-1", contract.tenantId)
        assertEquals("business-1", contract.businessId)
        assertEquals("user-1", contract.userId)
        assertEquals("membership-1", contract.membershipId)
        assertEquals(setOf("role-1"), contract.roleIds)
        assertEquals(
            setOf("sales.create", "parties.view"),
            contract.capabilityKeys,
        )
    }
}
