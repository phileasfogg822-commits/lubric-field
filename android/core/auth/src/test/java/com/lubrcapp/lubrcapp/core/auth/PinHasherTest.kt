package com.lubrcapp.lubrcapp.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher()

    @Test
    fun verifierAcceptsCorrectPinAndRejectsWrongPin() {
        val verifier = hasher.create("2468".toCharArray())

        assertTrue(hasher.verify("2468".toCharArray(), verifier))
        assertFalse(hasher.verify("1357".toCharArray(), verifier))
        assertNotEquals("2468", verifier.hashBase64)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonNumericPinIsRejected() {
        hasher.create("12ab".toCharArray())
    }
}
