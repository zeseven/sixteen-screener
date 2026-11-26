package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.RequestCounterRepository
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant

fun requestCounterRepositoryContract(newRepository: suspend () -> RequestCounterRepository) = funSpec {
    val source = SourceId.of("finnhub")
    val other = SourceId.of("logoapi")
    val now = Instant.parse("2026-11-14T12:00:30Z")
    val perMinute = RequestLimit(LimitInterval.MINUTE, 60)
    val perDay = RequestLimit(LimitInterval.DAY, 100)
    val perMonth = RequestLimit(LimitInterval.MONTH, 1000)

    test("grants all requested calls within the limit") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perMinute), 10, now) shouldBe 10
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 10
    }

    test("grants exactly the remaining calls when the limit is reached") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perMinute), 60, now) shouldBe 60
        repository.tryAcquire(source, listOf(perMinute), 1, now) shouldBe 0
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 60
    }

    test("partial grant") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perMinute), 55, now)
        repository.tryAcquire(source, listOf(perMinute), 10, now) shouldBe 5
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 60
    }

    test("the strictest interval wins and the grant is added to every window") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perDay), 95, now)

        repository.tryAcquire(source, listOf(perMinute, perDay, perMonth), 20, now) shouldBe 5

        repository.usage(source, listOf(perMinute, perDay, perMonth), now).map { it.used } shouldContainExactly listOf(5, 100, 5)
    }

    test("a new window starts with a fresh count") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perMinute), 60, now)
        repository.tryAcquire(source, listOf(perMinute), 5, now + Duration.ofMinutes(1)) shouldBe 5
    }

    test("counters are kept per source") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perMinute), 60, now)
        repository.tryAcquire(other, listOf(perMinute), 5, now) shouldBe 5
    }

    test("a source without limits gets every call") {
        newRepository().tryAcquire(source, emptyList(), 1000, now) shouldBe 1000
    }

    test("a limit of zero grants nothing") {
        newRepository().tryAcquire(source, listOf(RequestLimit(LimitInterval.DAY, 0)), 3, now) shouldBe 0
    }

    test("concurrent reservations never exceed the limit") {
        val repository = newRepository()
        val granted = coroutineScope {
            (1..25).map { async { repository.tryAcquire(source, listOf(perMinute), 3, now) } }.awaitAll()
        }
        granted.sum() shouldBe 60
    }

    test("saturation fills the current windows only") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(perMinute, perDay), 3, now)

        repository.saturate(source, listOf(perMinute, perDay), now)

        repository.usage(source, listOf(perMinute, perDay), now).map { it.used } shouldContainExactly listOf(60, 100)
        repository.tryAcquire(source, listOf(perMinute), 1, now + Duration.ofMinutes(1)) shouldBe 1
    }

    test("saturation of windows without counters creates them at the limit") {
        val repository = newRepository()
        repository.saturate(source, listOf(perMinute), now)
        repository.tryAcquire(source, listOf(perMinute), 1, now) shouldBe 0
    }

    test("saturation never lowers a count above the limit") {
        val repository = newRepository()
        repository.tryAcquire(source, listOf(RequestLimit(LimitInterval.MINUTE, 100)), 70, now)

        repository.saturate(source, listOf(perMinute), now)

        repository.usage(source, listOf(perMinute), now).single().used shouldBe 70
    }

    test("a counter without a limit counts every call and never refuses one") {
        val repository = newRepository()
        val counter = RequestLimit(LimitInterval.DAY, null)

        repository.tryAcquire(source, listOf(counter), 1000, now) shouldBe 1000
        repository.tryAcquire(source, listOf(counter), 5, now) shouldBe 5

        repository.usage(source, listOf(counter), now).single().used shouldBe 1005
    }

    test("a counter without a limit resets with its window") {
        val repository = newRepository()
        val counter = RequestLimit(LimitInterval.DAY, null)
        repository.tryAcquire(source, listOf(counter), 3, now)

        repository.usage(source, listOf(counter), now + Duration.ofDays(1)).single().used shouldBe 0
    }

    test("a counter without a limit next to a limit counts the granted calls only") {
        val repository = newRepository()
        val counter = RequestLimit(LimitInterval.DAY, null)

        repository.tryAcquire(source, listOf(perMinute, counter), 70, now) shouldBe 60

        repository.usage(source, listOf(perMinute, counter), now).map { it.used } shouldContainExactly listOf(60, 60)
    }

    test("saturation leaves a counter without a limit unchanged") {
        val repository = newRepository()
        val counter = RequestLimit(LimitInterval.DAY, null)
        repository.tryAcquire(source, listOf(counter), 2, now)

        repository.saturate(source, listOf(counter), now)

        repository.usage(source, listOf(counter), now).single().used shouldBe 2
        repository.tryAcquire(source, listOf(counter), 1, now) shouldBe 1
    }

    test("a source without a total counter has a total of zero") {
        newRepository().totals(listOf(source, other)) shouldBe mapOf(source to 0L, other to 0L)
    }

    test("no sources, no totals") {
        newRepository().totals(emptyList()) shouldBe emptyMap()
    }

    test("total counters add up per source and are independent of the limit windows") {
        val repository = newRepository()
        repository.incrementTotal(source, 3)
        repository.incrementTotal(source, 2)
        repository.incrementTotal(other, 1)
        repository.incrementTotal(other, 0)

        repository.totals(listOf(source, other)) shouldBe mapOf(source to 5L, other to 1L)
        repository.usage(source, listOf(perMinute), now).single().used shouldBe 0
    }

    test("concurrent total increments are not lost") {
        val repository = newRepository()
        coroutineScope { (1..20).map { async { repository.incrementTotal(source, 1) } }.awaitAll() }

        repository.totals(listOf(source)) shouldBe mapOf(source to 20L)
    }

    test("usage reports zero and the window end for absent counters") {
        val usage = newRepository().usage(source, listOf(perMinute, perMonth), now)
        usage.map { it.used } shouldContainExactly listOf(0, 0)
        usage.map { it.resetsAt } shouldContainExactly listOf(
            Instant.parse("2026-11-14T12:01:00Z"),
            Instant.parse("2026-12-01T00:00:00Z"),
        )
    }
}
