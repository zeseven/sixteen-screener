package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant

class InMemoryRequestCounterRepositoryTest : FunSpec({
    val source = SourceId.of("finnhub")
    val now = Instant.parse("2026-11-14T12:00:30Z")
    val perMinute = RequestLimit(LimitInterval.MINUTE, 60)
    val perDay = RequestLimit(LimitInterval.DAY, 100)

    test("grants all requested calls within the limit") {
        val repository = InMemoryRequestCounterRepository()
        repository.tryAcquire(source, listOf(perMinute), 10, now) shouldBe 10
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 10
    }

    test("grants exactly the remaining calls when the limit is reached") {
        val repository = InMemoryRequestCounterRepository()
        repository.tryAcquire(source, listOf(perMinute), 60, now) shouldBe 60
        repository.tryAcquire(source, listOf(perMinute), 1, now) shouldBe 0
    }

    test("partial grant") {
        val repository = InMemoryRequestCounterRepository()
        repository.setCount(source, LimitInterval.MINUTE, now, 55)
        repository.tryAcquire(source, listOf(perMinute), 10, now) shouldBe 5
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 60
    }

    test("the strictest interval wins and the grant is added to every window") {
        val repository = InMemoryRequestCounterRepository()
        repository.setCount(source, LimitInterval.DAY, now, 95)

        repository.tryAcquire(source, listOf(perMinute, perDay), 20, now) shouldBe 5

        repository.usage(source, listOf(perMinute, perDay), now).map { it.used } shouldContainExactly listOf(5, 100)
    }

    test("a new window starts with a fresh count") {
        val repository = InMemoryRequestCounterRepository()
        repository.tryAcquire(source, listOf(perMinute), 60, now)
        repository.tryAcquire(source, listOf(perMinute), 5, now + Duration.ofMinutes(1)) shouldBe 5
    }

    test("a source without limits gets every call and stores nothing") {
        val repository = InMemoryRequestCounterRepository()
        repository.tryAcquire(source, emptyList(), 1000, now) shouldBe 1000
    }

    test("concurrent reservations never exceed the limit") {
        val repository = InMemoryRequestCounterRepository()
        val granted = coroutineScope {
            (1..50).map { async { repository.tryAcquire(source, listOf(perMinute), 3, now) } }.awaitAll()
        }
        granted.sum() shouldBe 60
    }

    test("saturation fills the current windows only") {
        val repository = InMemoryRequestCounterRepository()
        repository.tryAcquire(source, listOf(perMinute, perDay), 3, now)

        repository.saturate(source, listOf(perMinute, perDay), now)

        repository.usage(source, listOf(perMinute, perDay), now).map { it.used } shouldContainExactly listOf(60, 100)
        repository.tryAcquire(source, listOf(perMinute), 1, now + Duration.ofMinutes(1)) shouldBe 1
    }

    test("saturation never lowers a count above the limit") {
        val repository = InMemoryRequestCounterRepository()
        repository.setCount(source, LimitInterval.MINUTE, now, 70)
        repository.saturate(source, listOf(perMinute), now)
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 70
    }

    test("usage reports zero and the window end for absent counters") {
        val usage = InMemoryRequestCounterRepository().usage(source, listOf(perMinute), now).single()
        usage.used shouldBe 0
        usage.resetsAt shouldBe Instant.parse("2026-11-14T12:01:00Z")
    }
})
