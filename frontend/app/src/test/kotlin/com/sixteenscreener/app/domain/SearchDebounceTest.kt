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
class SearchDebounceTest {

    private data class Emitted(val atMillis: Long, val request: SearchRequest)

    private val input = MutableSharedFlow<String>()
    private val submitted = MutableSharedFlow<String>()
    private val emitted = mutableListOf<Emitted>()

    private fun runDebounceTest(block: suspend TestScope.() -> Unit) = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            input.debounceSearch(submitted).collect { emitted += Emitted(currentTime, it) }
        }
        block()
    }

    private suspend fun TestScope.type(text: String, thenWaitMillis: Long = 0) {
        input.emit(text)
        if (thenWaitMillis > 0) advanceTimeBy(thenWaitMillis)
    }

    private suspend fun TestScope.submit(text: String, thenWaitMillis: Long = 0) {
        submitted.emit(text)
        if (thenWaitMillis > 0) advanceTimeBy(thenWaitMillis)
    }

    private fun TestScope.settle() = advanceTimeBy(10_000)

    private fun query(text: String) = SearchRequest.Query(text)

    @Test
    fun `one character is sent after the pause`() = runDebounceTest {
        type("f", thenWaitMillis = 499)
        assertEquals(emptyList<Emitted>(), emitted)

        settle()
        assertEquals(listOf(Emitted(500, query("f"))), emitted)
    }

    @Test
    fun `letters typed less than the pause apart send nothing`() = runDebounceTest {
        type("a", thenWaitMillis = 400)
        type("ap", thenWaitMillis = 499)
        type("app", thenWaitMillis = 300)
        type("appl", thenWaitMillis = 499)

        assertEquals(emptyList<Emitted>(), emitted)
    }

    @Test
    fun `latest query is sent once the pause has passed after the last letter`() = runDebounceTest {
        type("a", thenWaitMillis = 100)
        type("ap", thenWaitMillis = 100)
        type("app", thenWaitMillis = 100)
        type("appl", thenWaitMillis = 100)
        type("apple")
        settle()

        assertEquals(listOf(Emitted(900, query("apple"))), emitted)
    }

    @Test
    fun `each pause in typing sends the query typed so far`() = runDebounceTest {
        type("apple", thenWaitMillis = 600)
        type("apple i", thenWaitMillis = 100)
        type("apple in")
        settle()

        assertEquals(listOf(Emitted(500, query("apple")), Emitted(1_200, query("apple in"))), emitted)
    }

    @Test
    fun `whitespace only input sends nothing`() = runDebounceTest {
        type(" ")
        type("   ")
        settle()

        assertEquals(listOf(Emitted(0, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `query is trimmed and whitespace only input clears`() = runDebounceTest {
        type("  f  ", thenWaitMillis = 2_000)
        type("     ")
        type(" gm ")
        settle()

        assertEquals(listOf(query("f"), SearchRequest.Clear, query("gm")), emitted.map { it.request })
    }

    @Test
    fun `changes of surrounding whitespace only make no new request and do not restart the pause`() = runDebounceTest {
        type("app", thenWaitMillis = 300)
        type("app ")
        settle()

        assertEquals(listOf(Emitted(500, query("app"))), emitted)
    }

    @Test
    fun `deleting back to empty clears at once and drops the pending query`() = runDebounceTest {
        type("a", thenWaitMillis = 600)
        type("ap", thenWaitMillis = 200)
        type("", thenWaitMillis = 100)
        settle()

        assertEquals(listOf(Emitted(500, query("a")), Emitted(800, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `empty input before anything was sent clears only once`() = runDebounceTest {
        type("")
        type("a", thenWaitMillis = 100)
        type("")
        settle()

        assertEquals(listOf(Emitted(0, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `search action sends the query at once`() = runDebounceTest {
        type("apple", thenWaitMillis = 100)
        submit("apple")

        assertEquals(listOf(Emitted(100, query("apple"))), emitted)
    }

    @Test
    fun `search action sends the query trimmed`() = runDebounceTest {
        type(" apple ", thenWaitMillis = 100)
        submit(" apple ")

        assertEquals(listOf(Emitted(100, query("apple"))), emitted)
    }

    @Test
    fun `pause after the search action sends no duplicate`() = runDebounceTest {
        type("apple", thenWaitMillis = 100)
        submit("apple")
        settle()

        assertEquals(listOf(Emitted(100, query("apple"))), emitted)
    }

    @Test
    fun `search action arriving before the typed query sends no duplicate`() = runDebounceTest {
        type("appl", thenWaitMillis = 100)
        submit("apple")
        type("apple")
        settle()

        assertEquals(listOf(Emitted(100, query("apple"))), emitted)
    }

    @Test
    fun `search action for an already sent query sends nothing`() = runDebounceTest {
        type("apple", thenWaitMillis = 600)
        submit("apple")
        settle()

        assertEquals(listOf(Emitted(500, query("apple"))), emitted)
    }

    @Test
    fun `search action on whitespace only input sends nothing`() = runDebounceTest {
        type("   ")
        submit("   ")
        submit("")
        settle()

        assertEquals(listOf(Emitted(0, SearchRequest.Clear)), emitted)
    }

    @Test
    fun `letters typed after the search action are sent after the pause`() = runDebounceTest {
        type("app", thenWaitMillis = 100)
        submit("app", thenWaitMillis = 100)
        type("appl")
        settle()

        assertEquals(listOf(Emitted(100, query("app")), Emitted(700, query("appl"))), emitted)
    }

    @Test
    fun `same query typed again after an unsent edit is not sent again`() = runDebounceTest {
        type("abc", thenWaitMillis = 600)
        type("abcd", thenWaitMillis = 200)
        type("abc")
        settle()

        assertEquals(listOf(Emitted(500, query("abc"))), emitted)
    }

    @Test
    fun `same query typed again after a sent edit is sent again`() = runDebounceTest {
        type("abc", thenWaitMillis = 600)
        type("abcd", thenWaitMillis = 600)
        type("abc")
        settle()

        assertEquals(listOf(query("abc"), query("abcd"), query("abc")), emitted.map { it.request })
    }

    @Test
    fun `same query typed again after clearing is sent again`() = runDebounceTest {
        type("abc", thenWaitMillis = 600)
        type("", thenWaitMillis = 100)
        type("abc")
        settle()

        assertEquals(
            listOf(Emitted(500, query("abc")), Emitted(600, SearchRequest.Clear), Emitted(1_200, query("abc"))),
            emitted,
        )
    }

    @Test
    fun `minimum length below one is rejected so an empty query is never sent`() {
        assertThrows(IllegalArgumentException::class.java) { input.debounceSearch(minLength = 0) }
    }
}
