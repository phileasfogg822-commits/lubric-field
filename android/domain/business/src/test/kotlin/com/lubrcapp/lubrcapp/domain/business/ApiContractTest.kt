package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractTest {

    @Test
    fun resourcePathsMatchSpecOrder() {
        assertEquals(
            listOf("/items", "/parties", "/orders", "/invoices", "/payments", "/stock", "/reports"),
            ApiResource.entries.map { it.path },
        )
    }

    @Test
    fun getIsAllowedForEveryResource() {
        ApiResource.entries.forEach { resource ->
            assertTrue(ApiContract.allows(resource, ApiMethod.GET))
        }
    }

    @Test
    fun postIsAllowedOnlyForWritableResources() {
        listOf(
            ApiResource.ITEMS,
            ApiResource.PARTIES,
            ApiResource.ORDERS,
            ApiResource.INVOICES,
            ApiResource.PAYMENTS,
        ).forEach { resource ->
            assertTrue(ApiContract.allows(resource, ApiMethod.POST))
        }
        assertFalse(ApiContract.allows(ApiResource.STOCK, ApiMethod.POST))
        assertFalse(ApiContract.allows(ApiResource.REPORTS, ApiMethod.POST))
    }

    @Test
    fun routeTableHasNineteenRoutes() {
        assertEquals(19, ApiContract.routes.size)
    }

    @Test
    fun pathForReturnsSpecPath() {
        assertEquals("/invoices", ApiContract.pathFor(ApiResource.INVOICES))
        assertEquals("/reports", ApiContract.pathFor(ApiResource.REPORTS))
    }

    @Test
    fun webhookEventNamesMatchSpec() {
        assertEquals(
            listOf("invoice.created", "payment.received", "order.created", "stock.low"),
            WebhookEvent.entries.map { it.eventName },
        )
    }

    @Test
    fun apiRequestCarriesScopeAndOptionalRecordId() {
        val request = ApiRequest(
            route = ApiRoute(ApiResource.ORDERS, ApiMethod.GET),
            scope = TenantScope("t1", "b1"),
            recordId = "o9",
        )
        assertEquals("t1", request.scope.tenantId)
        assertEquals("o9", request.recordId)
    }

    @Test
    fun apiRequestWithoutRecordIdIsACollectionRequest() {
        val request = ApiRequest(
            route = ApiRoute(ApiResource.ITEMS, ApiMethod.GET),
            scope = TenantScope("t1", "b1"),
        )
        assertEquals(null, request.recordId)
    }
}
