package com.sixteenscreener.adapter.outbound.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private fun TestScope.pacer(minInterval: Duration = 1.seconds) = RequestPacer(minInterval, testTimeSource)

private fun TestScope.elapsed(): Long = currentTime

class RequestPacerTest : FunSpec({
    test("the first request starts at once") {
        runTest {
            pacer().awaitTurn()

            elapsed() shouldBe 0
        }
    }

    test("requests arriving together start one interval apart in arrival order") {
        runTest {
            val pacer = pacer()

            val starts = List(4) { index -> async { pacer.awaitTurn().let { index to elapsed() } } }.awaitAll()

            starts shouldContainExactly listOf(0 to 0L, 1 to 1000L, 2 to 2000L, 3 to 3000L)
        }
    }

    test("a request arriving after the interval has passed doesn't wait") {
        runTest {
            val pacer = pacer()
            pacer.awaitTurn()

            delay(1500.milliseconds)
            pacer.awaitTurn()

            elapsed() shouldBe 1500
        }
    }

    test("a request arriving within the interval waits for the rest of it only") {
        runTest {
            val pacer = pacer()
            pacer.awaitTurn()

            delay(300.milliseconds)
            pacer.awaitTurn()

            elapsed() shouldBe 1000
        }
    }

    test("a waiting request is not refused and the next one keeps waiting behind it") {
        runTest {
            val pacer = pacer()
            val started = mutableListOf<Int>()
            pacer.awaitTurn()

            launch { pacer.awaitTurn(); started += 1 }
            launch { pacer.awaitTurn(); started += 2 }
            runCurrent()
            started shouldBe emptyList()

            advanceTimeBy(1001.milliseconds)
            started shouldContainExactly listOf(1)

            advanceTimeBy(1000.milliseconds)
            started shouldContainExactly listOf(1, 2)
        }
    }

    test("a cancelled waiting request gives its turn to the next one") {
        runTest {
            val pacer = pacer()
            pacer.awaitTurn()

            val cancelled = launch { pacer.awaitTurn() }
            val next = async { pacer.awaitTurn().let { elapsed() } }
            runCurrent()
            cancelled.cancel()

            next.await() shouldBe 1000
        }
    }

    test("the interval must be positive") {
        shouldThrow<IllegalArgumentException> { RequestPacer(Duration.ZERO) }
    }
})
