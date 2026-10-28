package com.lubrcapp.lubrcapp.domain.business

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNumberingTest {

    private val sequence = NumberingSequence("INVOICE", prefix = "INV")
    private val date = LocalDate.of(2026, 10, 27)

    @Test
    fun allocatedNumberCarriesDevicePrefixFirst() {
        val (_, number) = DeviceNumbering.allocate(sequence, DeviceNumberingState("DEV1"), date)
        assertEquals("DEV1-INV-2026-00001", number)
    }

    @Test
    fun allocationAdvancesWithinPeriod() {
        val state = DeviceNumberingState("DEV1", periodKey = "2026", lastIssued = 4)
        val (advanced, number) = DeviceNumbering.allocate(sequence, state, date)
        assertEquals("DEV1-INV-2026-00005", number)
        assertEquals(5L, advanced.lastIssued)
    }

    @Test
    fun allocationRestartsInNewPeriod() {
        val state = DeviceNumberingState("DEV1", periodKey = "2025", lastIssued = 9)
        val (_, number) = DeviceNumbering.allocate(sequence, state, date)
        assertEquals("DEV1-INV-2026-00001", number)
    }

    @Test
    fun differentDevicesNeverShareAFormattedNumber() {
        val (_, first) = DeviceNumbering.allocate(sequence, DeviceNumberingState("DEV1"), date)
        val (_, second) = DeviceNumbering.allocate(sequence, DeviceNumberingState("DEV2"), date)
        assertTrue(first != second)
        assertFalse(DeviceNumbering.hasCollision(listOf(first, second)))
    }

    @Test
    fun mergeCollapsesSameDeviceAndPeriodToMax() {
        val merged = DeviceNumbering.merge(
            listOf(
                DeviceNumberingState("DEV1", "2026", 3),
                DeviceNumberingState("DEV1", "2026", 7),
            ),
        )
        assertEquals(listOf(DeviceNumberingState("DEV1", "2026", 7)), merged)
    }

    @Test
    fun mergeKeepsDifferentDevicesSeparate() {
        val merged = DeviceNumbering.merge(
            listOf(
                DeviceNumberingState("DEV2", "2026", 2),
                DeviceNumberingState("DEV1", "2026", 5),
            ),
        )
        assertEquals(2, merged.size)
        assertEquals("DEV1", merged.first().deviceId)
    }

    @Test
    fun mergeSortsByDeviceThenPeriod() {
        val merged = DeviceNumbering.merge(
            listOf(
                DeviceNumberingState("DEV1", "2027", 1),
                DeviceNumberingState("DEV1", "2026", 1),
            ),
        )
        assertEquals(listOf("2026", "2027"), merged.map { it.periodKey })
    }

    @Test
    fun duplicateFormattedNumberIsACollision() {
        assertTrue(DeviceNumbering.hasCollision(listOf("DEV1-INV-2026-00001", "DEV1-INV-2026-00001")))
        assertFalse(DeviceNumbering.hasCollision(listOf("DEV1-INV-2026-00001", "DEV2-INV-2026-00001")))
    }
}
