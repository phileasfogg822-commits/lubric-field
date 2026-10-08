package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionalLayoutTest {

    @Test
    fun startAndEndPaddingMirrorInRtl() {
        val padding = directionalPadding(
            start = 24.dp,
            end = 12.dp,
        )

        assertEquals(24.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, padding.calculateLeftPadding(LayoutDirection.Rtl))
        assertEquals(24.dp, padding.calculateRightPadding(LayoutDirection.Rtl))
    }
}
