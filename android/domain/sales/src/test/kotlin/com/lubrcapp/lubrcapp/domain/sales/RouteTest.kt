package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.PartyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private fun dayWithThreeStops(): RouteDay {
        var day = RouteOps.createDay("route-1", "2026-10-27")
        day = RouteOps.addStop(day, "s1", PartyId("ali-autos"))
        day = RouteOps.addStop(day, "s2", PartyId("city-oil"))
        day = RouteOps.addStop(day, "s3", PartyId("ahmed-traders"))
        return day
    }

    @Test
    fun createDayStartsEmpty() {
        val day = RouteOps.createDay("route-1", "2026-10-27")
        assertTrue(day.stops.isEmpty())
        assertEquals("2026-10-27", day.date)
    }

    @Test
    fun addStopAssignsPositionsInOrder() {
        val day = dayWithThreeStops()
        assertEquals(listOf(0, 1, 2), day.stops.map { it.position })
        assertEquals("ali-autos", day.stops[0].partyId.value)
    }

    @Test
    fun addStopFollowsMaxPosition() {
        val day = RouteDay(
            id = "route-1",
            stops = listOf(
                RouteStop("s1", PartyId("a"), 5),
                RouteStop("s2", PartyId("b"), 2),
            ),
        )
        val updated = RouteOps.addStop(day, "s3", PartyId("c"))
        assertEquals(6, updated.stops.last().position)
    }

    @Test
    fun recordVisitSetsOutcomeAndNote() {
        val day = RouteOps.recordVisit(dayWithThreeStops(), "s2", VisitAction.ORDER, "ordered 10 cartons")
        assertEquals(VisitAction.ORDER, day.stops[1].outcome)
        assertEquals("ordered 10 cartons", day.stops[1].note)
        assertEquals(null, day.stops[0].outcome)
    }

    @Test
    fun recordVisitOnUnknownStopThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteOps.recordVisit(dayWithThreeStops(), "missing", VisitAction.VISITED)
        }
    }

    @Test
    fun recordVisitOverwritesPreviousOutcome() {
        var day = RouteOps.recordVisit(dayWithThreeStops(), "s1", VisitAction.NO_ORDER)
        day = RouteOps.recordVisit(day, "s1", VisitAction.RESCHEDULE, "closed today")
        assertEquals(VisitAction.RESCHEDULE, day.stops[0].outcome)
        assertEquals("closed today", day.stops[0].note)
    }

    @Test
    fun stopsInOrderSortsByPosition() {
        val day = RouteDay(
            id = "route-1",
            stops = listOf(
                RouteStop("s2", PartyId("b"), 1),
                RouteStop("s1", PartyId("a"), 0),
            ),
        )
        assertEquals(listOf("s1", "s2"), RouteOps.stopsInOrder(day).map { it.id })
    }

    @Test
    fun pendingAndCompletedSplitByOutcome() {
        var day = dayWithThreeStops()
        day = RouteOps.recordVisit(day, "s1", VisitAction.VISITED)
        day = RouteOps.recordVisit(day, "s3", VisitAction.CLOSED)
        assertEquals(listOf("s2"), RouteOps.pending(day).map { it.id })
        assertEquals(listOf("s1", "s3"), RouteOps.completed(day).map { it.id })
    }

    @Test
    fun operationsDoNotMutateTheOriginal() {
        val day = dayWithThreeStops()
        RouteOps.addStop(day, "s4", PartyId("khan-motors"))
        RouteOps.recordVisit(day, "s1", VisitAction.VISITED)
        assertTrue(day.stops.size == 3)
        assertEquals(null, day.stops[0].outcome)
    }
}
