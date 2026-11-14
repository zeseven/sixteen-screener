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
    fun `two characters make no request`() = runThrottleTest {
        type("a")
        type("ap")
        settle()

        assertEquals(listOf(Emitted(0, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `three characters make an immediate request`() = runThrottleTest {
        type("app")

        assertEquals(listOf(Emitted(0, query("app"))), emitted)
    }

    @Test
    fun `query is trimmed and whitespace only input clears`() = runThrottleTest {
        type("  ap  ")
        type("     ")
        type(" app ")

        assertEquals(listOf(SearchRequest.Clear, query("app")), emitted.map { it.request })
    }

    @Test
    fun `changes of surrounding whitespace only make no new request`() = runThrottleTest {
        type("app")
        type("app ", thenWaitMillis = 2_000)

        assertEquals(listOf(query("app")), emitted.map { it.request })
    }

    @Test
    fun `fast typing sends at most one request per second and the last query`() = runThrottleTest {
        type("app", thenWaitMillis = 100)
        type("appl", thenWaitMillis = 100)
        type("apple", thenWaitMillis = 100)
        type("apple i", thenWaitMillis = 1_000)
        type("apple in", thenWaitMillis = 100)
        type("apple inc")
        settle()

        assertEquals(
            listOf(Emitted(0, query("app")), Emitted(1_000, query("apple i")), Emitted(2_000, query("apple inc"))),
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
    fun `deleting below three characters clears at once and drops the pending query`() = runThrottleTest {
        type("app", thenWaitMillis = 200)
        type("appl", thenWaitMillis = 200)
        type("ap")
        settle()

        assertEquals(listOf(Emitted(0, query("app")), Emitted(400, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `query typed again after clearing still respects the interval`() = runThrottleTest {
        type("app", thenWaitMillis = 200)
        type("ap", thenWaitMillis = 100)
        type("sap")
        settle()

        assertEquals(
            listOf(Emitted(0, query("app")), Emitted(200, SearchRequest.Clear), Emitted(1_000, query("sap"))),
            emitted,
        )
    }
}
