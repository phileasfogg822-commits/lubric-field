package com.lubrcapp.lubrcapp.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarSelectionTest {

    @Test
    fun homeItemSelectedOnDashboard() {
        assertTrue(
            isBottomBarItemSelected(
                isHome = true,
                moduleId = "dashboard",
                selection = BottomNavSelection.HOME,
                selectedModuleId = null,
            ),
        )
    }

    @Test
    fun homeItemNotSelectedWhenAModuleIsOpen() {
        assertFalse(
            isBottomBarItemSelected(
                isHome = true,
                moduleId = "dashboard",
                selection = BottomNavSelection.HOME,
                selectedModuleId = "items",
            ),
        )
    }

    @Test
    fun openModuleItemSelected() {
        assertTrue(
            isBottomBarItemSelected(
                isHome = false,
                moduleId = "items",
                selection = BottomNavSelection.HOME,
                selectedModuleId = "items",
            ),
        )
    }

    @Test
    fun otherModuleItemNotSelected() {
        assertFalse(
            isBottomBarItemSelected(
                isHome = false,
                moduleId = "items",
                selection = BottomNavSelection.HOME,
                selectedModuleId = "parties",
            ),
        )
    }

    @Test
    fun noModuleItemSelectedOnDashboard() {
        assertFalse(
            isBottomBarItemSelected(
                isHome = false,
                moduleId = "items",
                selection = BottomNavSelection.HOME,
                selectedModuleId = null,
            ),
        )
    }

    @Test
    fun noItemSelectedOnMoreList() {
        assertFalse(
            isBottomBarItemSelected(
                isHome = true,
                moduleId = "dashboard",
                selection = BottomNavSelection.MORE,
                selectedModuleId = null,
            ),
        )
        assertFalse(
            isBottomBarItemSelected(
                isHome = false,
                moduleId = "items",
                selection = BottomNavSelection.MORE,
                selectedModuleId = null,
            ),
        )
    }
}
