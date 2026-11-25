package com.sixteenscreener.app.ui.watchlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToRemoveThresholdTest {

    @Test
    fun `distance is half of the row width`() {
        assertEquals(200f, SwipeToRemoveThreshold.distance(400f), 0f)
    }

    @Test
    fun `offset below half of the row width is not enough`() {
        assertFalse(SwipeToRemoveThreshold.isReached(offset = -199f, rowWidth = 400f))
    }

    @Test
    fun `offset of exactly half of the row width is enough`() {
        assertTrue(SwipeToRemoveThreshold.isReached(offset = -200f, rowWidth = 400f))
    }

    @Test
    fun `offset beyond half of the row width is enough`() {
        assertTrue(SwipeToRemoveThreshold.isReached(offset = -390f, rowWidth = 400f))
    }

    @Test
    fun `positive offsets are measured like negative ones`() {
        assertTrue(SwipeToRemoveThreshold.isReached(offset = 200f, rowWidth = 400f))
        assertFalse(SwipeToRemoveThreshold.isReached(offset = 199f, rowWidth = 400f))
    }

    @Test
    fun `no offset is never enough`() {
        assertFalse(SwipeToRemoveThreshold.isReached(offset = 0f, rowWidth = 400f))
    }

    @Test
    fun `unknown row width is never enough`() {
        assertFalse(SwipeToRemoveThreshold.isReached(offset = -200f, rowWidth = 0f))
        assertFalse(SwipeToRemoveThreshold.isReached(offset = 0f, rowWidth = 0f))
    }
}
