package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.time.Instant

class LimitIntervalTest : FunSpec({
    val now = Instant.parse("2026-02-28T23:59:59.999Z")

    withData(
        nameFn = { (interval, _, _) -> interval.name },
        Triple(LimitInterval.MINUTE, "2026-02-28T23:59:00Z", "2026-03-01T00:00:00Z"),
        Triple(LimitInterval.HOUR, "2026-02-28T23:00:00Z", "2026-03-01T00:00:00Z"),
        Triple(LimitInterval.DAY, "2026-02-28T00:00:00Z", "2026-03-01T00:00:00Z"),
        Triple(LimitInterval.MONTH, "2026-02-01T00:00:00Z", "2026-03-01T00:00:00Z"),
    ) { (interval, start, end) ->
        interval.windowStart(now) shouldBe Instant.parse(start)
        interval.windowEnd(now) shouldBe Instant.parse(end)
    }

    test("an instant on a window boundary starts a new window") {
        val boundary = Instant.parse("2026-03-01T00:00:00Z")
        LimitInterval.MONTH.windowStart(boundary) shouldBe boundary
        LimitInterval.MONTH.windowEnd(boundary) shouldBe Instant.parse("2026-04-01T00:00:00Z")
    }
})
