package com.twowork.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun `accepts a fully compliant password`() {
        assertTrue(PasswordPolicy.isValid("TestPass12345!"))
        assertTrue(PasswordPolicy.unmet("TestPass12345!").isEmpty())
    }

    @Test
    fun `flags a missing symbol`() {
        assertFalse(PasswordPolicy.isValid("TestPass12345"))
        assertEquals(listOf("A symbol"), PasswordPolicy.unmet("TestPass12345"))
    }

    @Test
    fun `flags too-short and missing classes`() {
        // "short" -> too short, no upper, no digit, no symbol (lower-case present)
        val unmet = PasswordPolicy.unmet("short")
        assertTrue(unmet.contains("At least 12 characters"))
        assertTrue(unmet.contains("An upper-case letter"))
        assertTrue(unmet.contains("A number"))
        assertTrue(unmet.contains("A symbol"))
        assertFalse(unmet.contains("A lower-case letter"))
    }
}
