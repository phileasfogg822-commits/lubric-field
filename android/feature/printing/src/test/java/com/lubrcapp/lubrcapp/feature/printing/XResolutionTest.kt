package com.lubrcapp.lubrcapp.feature.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class XResolutionTest {

    @Test
    fun leftAlignsAtMargin() {
        assertEquals(48f, resolveX(LayoutAlign.LEFT, 100f, 595f, 48f, 48f), 0.001f)
    }

    @Test
    fun centerAlignsMidpoint() {
        assertEquals(247.5f, resolveX(LayoutAlign.CENTER, 100f, 595f, 48f, 48f), 0.001f)
    }

    @Test
    fun rightAlignsBeforeMargin() {
        assertEquals(447f, resolveX(LayoutAlign.RIGHT, 100f, 595f, 48f, 48f), 0.001f)
    }

    @Test
    fun zeroWidthTextCentersOnPage() {
        assertEquals(297.5f, resolveX(LayoutAlign.CENTER, 0f, 595f, 48f, 48f), 0.001f)
    }
}
