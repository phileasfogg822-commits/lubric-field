package com.lubrcapp.lubrcapp.feature.parties

import org.junit.Assert.assertEquals
import org.junit.Test

class PartyAvatarTest {

    @Test
    fun twoWordsGiveTwoInitials() {
        assertEquals("AK", initialsFor("Ahmed Khan"))
    }

    @Test
    fun oneWordGivesOneInitial() {
        assertEquals("A", initialsFor("Ayesha"))
    }

    @Test
    fun blankNameGivesPlaceholder() {
        assertEquals("?", initialsFor(""))
        assertEquals("?", initialsFor("   "))
    }

    @Test
    fun extraSpacesAreIgnored() {
        assertEquals("AR", initialsFor("  Ali   Raza  "))
    }
}
