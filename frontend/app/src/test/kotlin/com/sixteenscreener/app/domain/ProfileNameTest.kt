package com.sixteenscreener.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNameTest {

    private fun validName(raw: String) = (ProfileName.parse(raw) as ProfileNameValidation.Valid).name.value

    @Test
    fun `empty name is blank`() {
        assertEquals(ProfileNameValidation.Blank, ProfileName.parse(""))
    }

    @Test
    fun `whitespace only name is blank`() {
        assertEquals(ProfileNameValidation.Blank, ProfileName.parse(" \t\n "))
    }

    @Test
    fun `one character is valid`() {
        assertEquals("a", validName("a"))
    }

    @Test
    fun `forty characters are valid`() {
        val name = "a".repeat(40)

        assertEquals(name, validName(name))
    }

    @Test
    fun `forty one characters are too long`() {
        assertEquals(ProfileNameValidation.TooLong, ProfileName.parse("a".repeat(41)))
    }

    @Test
    fun `surrounding whitespace is trimmed before validation`() {
        assertEquals("a".repeat(40), validName("  ${"a".repeat(40)}  "))
    }

    @Test
    fun `inner whitespace is kept`() {
        assertEquals("Anna Maria", validName(" Anna Maria "))
    }

    @Test
    fun `length is counted in code points so forty surrogate pairs are valid`() {
        val name = "😀".repeat(40)

        assertEquals(name, validName(name))
    }

    @Test
    fun `forty one surrogate pairs are too long`() {
        assertEquals(ProfileNameValidation.TooLong, ProfileName.parse("😀".repeat(41)))
    }

    @Test
    fun `non latin letters are valid`() {
        assertEquals("Виктор", validName("Виктор"))
    }
}
