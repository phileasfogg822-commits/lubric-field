package com.lubrcapp.lubrcapp.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorUtilsTest {

    @Test
    fun blendEndpointsAreStable() {
        val source = 0xFF123456.toInt()
        val target = 0xFFFFFFFF.toInt()

        assertEquals(source, blendArgb(source, target, 0f))
        assertEquals(target, blendArgb(source, target, 1f))
    }

    @Test
    fun contrastChoosesDarkTextForLightBackground() {
        assertEquals(ARGB_BLACK, contrastColor(0xFFFFFFFF.toInt()))
    }

    @Test
    fun contrastChoosesLightTextForDarkBackground() {
        assertEquals(ARGB_WHITE, contrastColor(0xFF101010.toInt()))
    }
}
