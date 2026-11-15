package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.DataType
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
})
