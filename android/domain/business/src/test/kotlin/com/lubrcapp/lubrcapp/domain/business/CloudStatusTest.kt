package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStatusTest {

    private val tenant = TenantScope("t1", "b1")
    private val ready = CloudStatus(
        CloudMode.CLOUD_CONNECTED,
        paired = true,
        tenant = tenant,
        surfaces = setOf(CloudSurface.PORTAL, CloudSurface.STORE),
    )

    @Test
    fun connectedPairedAndScopedIsReady() {
        assertTrue(CloudStatusOps.isCloudReady(ready))
    }

    @Test
    fun localOnlyIsNeverReady() {
        val local = CloudStatus(CloudMode.LOCAL_ONLY, paired = true, tenant = tenant)
        assertFalse(CloudStatusOps.isCloudReady(local))
    }

    @Test
    fun unpairedCloudIsNotReady() {
        val unpaired = ready.copy(paired = false)
        assertFalse(CloudStatusOps.isCloudReady(unpaired))
    }

    @Test
    fun cloudWithoutTenantIsNotReady() {
        val noTenant = ready.copy(tenant = null)
        assertFalse(CloudStatusOps.isCloudReady(noTenant))
    }

    @Test
    fun surfacesPassThroughWhenReady() {
        assertEquals(
            setOf(CloudSurface.PORTAL, CloudSurface.STORE),
            CloudStatusOps.availableSurfaces(ready),
        )
    }

    @Test
    fun surfacesAreEmptyWhenNotReady() {
        val local = ready.copy(mode = CloudMode.LOCAL_ONLY)
        assertTrue(CloudStatusOps.availableSurfaces(local).isEmpty())
    }

    @Test
    fun summaryIsLocalOnlyWhenNotReady() {
        assertEquals("Local only", CloudStatusOps.summary(ready.copy(paired = false)))
    }

    @Test
    fun summaryListsSurfacesWhenReady() {
        assertEquals("Cloud connected: PORTAL, STORE", CloudStatusOps.summary(ready))
    }
}
