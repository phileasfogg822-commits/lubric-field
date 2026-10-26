package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.PartyId

/**
 * Phase 27.1 — the salesperson day route (§109 Salesperson
 * Route). Pure and immutable: every operation returns a
 * new route, so the route list, visit recording, and the
 * later delivery/collection screens share one state shape
 * without a ViewModel.
 *
 * A route is an ordered list of stops for one day. A stop
 * names a party; its outcome starts empty and is recorded
 * when the salesperson acts (§109 visit actions: Visited,
 * Order, No Order, Closed, Reschedule). Visit tracking
 * times (§110) are NOT here — 27.1 records the action
 * only; started/ended times are a later package decision,
 * and GPS stays off by default per §110. No persistence
 * here: how a route is stored is a later package decision
 * against the record machinery and the sync whitelist.
 */
enum class VisitAction {
    VISITED,
    ORDER,
    NO_ORDER,
    CLOSED,
    RESCHEDULE,
}

data class RouteStop(
    val id: String,
    val partyId: PartyId,
    val position: Int,
    val outcome: VisitAction? = null,
    val note: String = "",
)

data class RouteDay(
    val id: String,
    val date: String = "",
    val stops: List<RouteStop> = emptyList(),
)

object RouteOps {
    fun createDay(id: String, date: String = ""): RouteDay =
        RouteDay(id = id, date = date)

    fun addStop(day: RouteDay, stopId: String, partyId: PartyId): RouteDay {
        val position = if (day.stops.isEmpty()) 0 else day.stops.maxOf { it.position } + 1
        return day.copy(stops = day.stops + RouteStop(stopId, partyId, position))
    }

    fun recordVisit(day: RouteDay, stopId: String, action: VisitAction, note: String = ""): RouteDay {
        val index = day.stops.indexOfFirst { it.id == stopId }
        require(index >= 0) { "Unknown stop: $stopId" }
        val updated = day.stops[index].copy(outcome = action, note = note)
        return day.copy(stops = day.stops.toMutableList().also { it[index] = updated })
    }

    fun stopsInOrder(day: RouteDay): List<RouteStop> =
        day.stops.sortedBy { it.position }

    fun pending(day: RouteDay): List<RouteStop> =
        stopsInOrder(day).filter { it.outcome == null }

    fun completed(day: RouteDay): List<RouteStop> =
        stopsInOrder(day).filter { it.outcome != null }
}
