package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 30.2 — API surface contracts (§228 + §229).
 * Pure types, not a server: no transport, no
 * endpoint, no credentials live here. The spec
 * names the paths only; the method table is the
 * contract this phase defines (read for every
 * resource, create for the five writable ones;
 * stock and reports are derived views, so they
 * are read-only). Every request carries a
 * [TenantScope] from 30.1 — a request without
 * a scope is not a valid platform request.
 */
enum class ApiResource(val path: String) {
    ITEMS("/items"),
    PARTIES("/parties"),
    ORDERS("/orders"),
    INVOICES("/invoices"),
    PAYMENTS("/payments"),
    STOCK("/stock"),
    REPORTS("/reports"),
}

enum class ApiMethod {
    GET,
    POST,
}

data class ApiRoute(
    val resource: ApiResource,
    val method: ApiMethod,
)

data class ApiRequest(
    val route: ApiRoute,
    val scope: TenantScope,
    val recordId: String? = null,
)

enum class WebhookEvent(val eventName: String) {
    INVOICE_CREATED("invoice.created"),
    PAYMENT_RECEIVED("payment.received"),
    ORDER_CREATED("order.created"),
    STOCK_LOW("stock.low"),
}

object ApiContract {
    private val writable = setOf(
        ApiResource.ITEMS,
        ApiResource.PARTIES,
        ApiResource.ORDERS,
        ApiResource.INVOICES,
        ApiResource.PAYMENTS,
    )

    val routes: List<ApiRoute> = ApiResource.entries.flatMap { resource ->
        listOf(ApiRoute(resource, ApiMethod.GET)) +
            if (resource in writable) listOf(ApiRoute(resource, ApiMethod.POST)) else emptyList()
    }

    fun allows(resource: ApiResource, method: ApiMethod): Boolean =
        routes.any { it.resource == resource && it.method == method }

    fun pathFor(resource: ApiResource): String = resource.path
}
