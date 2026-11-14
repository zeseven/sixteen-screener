package com.sixteenscreener.app.data.remote.logo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendRelativeUrlMapperTest {

    private val mapper = BackendRelativeUrlMapper("https://backend.example.com")

    @Test
    fun `logo path is resolved against the backend URL`() {
        assertEquals("https://backend.example.com/logos/AAPL", mapper.resolve("/logos/AAPL"))
    }

    @Test
    fun `symbol with a suffix keeps the dot`() {
        assertEquals("https://backend.example.com/logos/SAP.DE", mapper.resolve("/logos/SAP.DE"))
    }

    @Test
    fun `absolute URLs are left unchanged`() {
        assertNull(mapper.resolve("https://cdn.example.com/logo.svg"))
    }

    @Test
    fun `protocol relative URLs are left unchanged`() {
        assertNull(mapper.resolve("//cdn.example.com/logo.svg"))
    }

    @Test
    fun `non path strings are left unchanged`() {
        assertNull(mapper.resolve("android.resource://com.sixteenscreener.app/1"))
    }
}
