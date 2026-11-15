package com.sixteenscreener.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchThrottleTest {

    private data class Emitted(val atMillis: Long, val request: SearchRequest)

    private val input = MutableSharedFlow<String>()
    private val emitted = mutableListOf<Emitted>()

    private fun runThrottleTest(block: suspend TestScope.() -> Unit) = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            input.throttleSearch().collect { emitted += Emitted(currentTime, it) }
        }
        block()
    }

    private suspend fun TestScope.type(text: String, thenWaitMillis: Long = 0) {
        input.emit(text)
        if (thenWaitMillis > 0) advanceTimeBy(thenWaitMillis)
    }

    private fun TestScope.settle() = advanceTimeBy(10_000)

    private fun query(text: String) = SearchRequest.Query(text)

    @Test
    fun `one character makes an immediate request`() = runThrottleTest {
        type("f")

        assertEquals(listOf(Emitted(0, query("f"))), emitted)
    }

    @Test
    fun `two characters make an immediate request`() = runThrottleTest {
        type("gm")

        assertEquals(listOf(Emitted(0, query("gm"))), emitted)
    }

    @Test
    fun `second character within the interval is sent when the second has passed`() = runThrottleTest {
        type("g", thenWaitMillis = 200)
        type("gm")
        settle()

        assertEquals(listOf(Emitted(0, query("g")), Emitted(1_000, query("gm"))), emitted)
    }

    @Test
    fun `whitespace only input sends nothing`() = runThrottleTest {
        type(" ")
        type("   ")
        settle()

        assertEquals(listOf(Emitted(0, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `query is trimmed and whitespace only input clears`() = runThrottleTest {
        type("  f  ", thenWaitMillis = 2_000)
        type("     ")
        type(" gm ")

        assertEquals(listOf(query("f"), SearchRequest.Clear, query("gm")), emitted.map { it.request })
    }

    @Test
    fun `changes of surrounding whitespace only make no new request`() = runThrottleTest {
        type("app")
        type("app ", thenWaitMillis = 2_000)

        assertEquals(listOf(query("app")), emitted.map { it.request })
    }

    @Test
    fun `fast typing from the first letter sends at most one request per second and the last query`() = runThrottleTest {
        type("a", thenWaitMillis = 100)
        type("ap", thenWaitMillis = 100)
        type("app", thenWaitMillis = 100)
        type("appl", thenWaitMillis = 100)
        type("apple", thenWaitMillis = 100)
        type("apple i", thenWaitMillis = 1_000)
        type("apple in", thenWaitMillis = 100)
        type("apple inc")
        settle()

        assertEquals(
            listOf(Emitted(0, query("a")), Emitted(1_000, query("apple i")), Emitted(2_000, query("apple inc"))),
            emitted,
        )
    }

    @Test
    fun `query after a quiet second is sent immediately`() = runThrottleTest {
        type("app", thenWaitMillis = 2_500)
        type("apple")

        assertEquals(listOf(Emitted(0, query("app")), Emitted(2_500, query("apple"))), emitted)
    }

    @Test
    fun `deleting back to empty clears at once and drops the pending query`() = runThrottleTest {
        type("a", thenWaitMillis = 200)
        type("ap", thenWaitMillis = 200)
        type("a", thenWaitMillis = 100)
        type("")
        settle()

        assertEquals(listOf(Emitted(0, query("a")), Emitted(500, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `query typed again after clearing still respects the interval`() = runThrottleTest {
        type("a", thenWaitMillis = 200)
        type("", thenWaitMillis = 100)
        type("s")
        settle()

        assertEquals(
            listOf(Emitted(0, query("a")), Emitted(200, SearchRequest.Clear), Emitted(1_000, query("s"))),
            emitted,
        )
    }

    @Test
    fun `minimum length below one is rejected so an empty query is never sent`() {
        assertThrows(IllegalArgumentException::class.java) { input.throttleSearch(minLength = 0) }
    }
}
