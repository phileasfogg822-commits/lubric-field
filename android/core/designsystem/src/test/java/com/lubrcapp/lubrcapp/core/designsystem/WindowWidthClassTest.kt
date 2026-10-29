package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowWidthClassTest {

    @Test
    fun compactBelow600() {
        assertEquals(WindowWidthClass.COMPACT, windowWidthClass(0f))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClass(599.9f))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClass(-10f))
    }

    @Test
    fun mediumFrom600Below840() {
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClass(600f))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClass(839.9f))
    }

    @Test
    fun expandedFrom840() {
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClass(840f))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClass(1200f))
    }

    @Test
    fun dpOverloadMatchesFloat() {
        assertEquals(WindowWidthClass.COMPACT, windowWidthClass(599.dp))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClass(600.dp))
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClass(840.dp))
    }

    @Test
    fun onlyCompactIsNotTablet() {
        assertEquals(false, WindowWidthClass.COMPACT.isTablet)
        assertEquals(true, WindowWidthClass.MEDIUM.isTablet)
        assertEquals(true, WindowWidthClass.EXPANDED.isTablet)
    }
}
