package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.domain.model.RequestTrigger.AUTO
import com.sixteenscreener.domain.model.RequestTrigger.MANUAL
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.time.Duration

class FreshnessPolicyTest : FunSpec({
    val clock = MutableClock()
    val policy = FreshnessPolicy(clock)
    val now = clock.now()

    test("data fetched just now is fresh") {
        policy.isFresh(now) shouldBe true
    }

    test("data fetched 59:59.999 ago is fresh") {
        policy.isFresh(now - Duration.ofHours(1) + Duration.ofMillis(1)) shouldBe true
    }

    test("data fetched exactly 1 hour ago is stale") {
        policy.isFresh(now - Duration.ofHours(1)) shouldBe false
    }

    test("data fetched more than 1 hour ago is stale") {
        policy.isFresh(now - Duration.ofHours(2)) shouldBe false
    }

    test("data fetched in the future is fresh") {
        policy.isFresh(now + Duration.ofMinutes(1)) shouldBe true
    }

    test("watchlist ratios younger than 1 day are up to date") {
        policy.isWatchlistRatiosUpToDate(now - Duration.ofDays(1) + Duration.ofMillis(1)) shouldBe true
        policy.isWatchlistRatiosUpToDate(now - Duration.ofHours(2)) shouldBe true
    }

    test("watchlist ratios of exactly 1 day or older need a refresh") {
        policy.isWatchlistRatiosUpToDate(now - Duration.ofDays(1)) shouldBe false
        policy.isWatchlistRatiosUpToDate(now - Duration.ofDays(3)) shouldBe false
    }

    test("company profile older than 14 days but younger than 90 days is not expired") {
        policy.isProfileExpired(now - Duration.ofDays(14)) shouldBe false
        policy.isProfileExpired(now - Duration.ofDays(90) + Duration.ofMillis(1)) shouldBe false
    }

    test("company profile exactly 90 days old is expired") {
        policy.isProfileExpired(now - Duration.ofDays(90)) shouldBe true
    }

    test("company profile entries expire 90 days after fetch") {
        policy.cacheExpireAt(DataType.COMPANY_PROFILE, now) shouldBe now + Duration.ofDays(90)
    }

    context("other cache entries expire 14 days after fetch") {
        withData(nameFn = { it.name }, DataType.QUOTE, DataType.RATIOS, DataType.BALANCE_SHEET) { type ->
            policy.cacheExpireAt(type, now) shouldBe now + Duration.ofDays(14)
        }
    }

    context("whether the source is called") {
        data class Case(
            val automaticRequests: Boolean,
            val trigger: RequestTrigger,
            val cacheAge: Duration?,
            val autoRefreshAfter: Duration?,
            val expected: Boolean,
        )

        val fresh = Duration.ofMinutes(10)
        val stale = Duration.ofHours(2)
        val hour = Duration.ofHours(1)

        withData(
            nameFn = {
                "automatic requests ${if (it.automaticRequests) "on" else "off"}, ${it.trigger}, " +
                    "cache ${it.cacheAge ?: "missing"}, auto refresh after ${it.autoRefreshAfter ?: "never"}: " +
                    if (it.expected) "call" else "no call"
            },
            Case(true, AUTO, null, null, expected = true),
            Case(true, AUTO, fresh, null, expected = false),
            Case(true, AUTO, stale, null, expected = false),
            Case(true, AUTO, Duration.ofDays(400), null, expected = false),
            Case(true, AUTO, null, hour, expected = true),
            Case(true, AUTO, fresh, hour, expected = false),
            Case(true, AUTO, stale, hour, expected = true),
            Case(true, AUTO, hour, hour, expected = true),
            Case(true, AUTO, hour - Duration.ofMillis(1), hour, expected = false),
            Case(true, MANUAL, null, null, expected = true),
            Case(true, MANUAL, fresh, null, expected = true),
            Case(true, MANUAL, stale, null, expected = true),
            Case(true, MANUAL, fresh, hour, expected = true),
            Case(false, AUTO, null, null, expected = false),
            Case(false, AUTO, fresh, null, expected = false),
            Case(false, AUTO, stale, null, expected = false),
            Case(false, AUTO, null, hour, expected = false),
            Case(false, AUTO, stale, hour, expected = false),
            Case(false, AUTO, Duration.ofDays(400), Duration.ofDays(90), expected = false),
            Case(false, MANUAL, null, null, expected = true),
            Case(false, MANUAL, fresh, null, expected = false),
            Case(false, MANUAL, hour - Duration.ofMillis(1), null, expected = false),
            Case(false, MANUAL, hour, null, expected = true),
            Case(false, MANUAL, stale, null, expected = true),
            Case(false, MANUAL, fresh, Duration.ofMinutes(5), expected = false),
            Case(false, MANUAL, stale, Duration.ofDays(1), expected = true),
        ) { case ->
            policy.shouldCallSource(
                automaticRequests = case.automaticRequests,
                trigger = case.trigger,
                cachedAt = case.cacheAge?.let { now - it },
                autoRefreshAfter = case.autoRefreshAfter,
            ) shouldBe case.expected
        }
    }
})
