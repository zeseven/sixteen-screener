package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class RatioCodeTest : FunSpec({
    test("33 ratios, 31 of them indicators, in settings order") {
        RatioCode.entries shouldHaveSize 33
        RatioCode.indicators shouldHaveSize 31
        RatioCode.indicators.first() shouldBe RatioCode.PE
        RatioCode.indicators.last() shouldBe RatioCode.BETA
    }

    test("per share ratios are currency amounts and not indicators") {
        RatioCode.entries.filterNot { it.isIndicator } shouldContainExactly
            listOf(RatioCode.EPS, RatioCode.BOOK_VALUE_PER_SHARE)
        RatioCode.entries.filter { it.unit == RatioUnit.CURRENCY } shouldContainExactly
            listOf(RatioCode.EPS, RatioCode.BOOK_VALUE_PER_SHARE)
        RatioCode.entries.filter { it.category == RatioCategory.PER_SHARE } shouldContainExactly
            listOf(RatioCode.EPS, RatioCode.BOOK_VALUE_PER_SHARE)
    }

    test("short names are at most 6 characters and unique") {
        RatioCode.entries.filter { it.shortName.length > RatioCode.MAX_SHORT_NAME_LENGTH }.shouldHaveSize(0)
        RatioCode.entries.map { it.shortName }.toSet() shouldHaveSize RatioCode.entries.size
    }

    test("names carry no percent suffix") {
        RatioCode.entries.filter { it.displayName.contains('%') }.shouldHaveSize(0)
    }

    test("parses codes by exact name") {
        RatioCode.parseOrNull("EV_EBITDA") shouldBe RatioCode.EV_EBITDA
        RatioCode.parseOrNull("pe").shouldBeNull()
        RatioCode.parseOrNull("UNKNOWN").shouldBeNull()
    }

    test("ratios built from values hold every code in display order") {
        val ratios = Ratios.of(mapOf(RatioCode.ROE to 12.0))

        ratios.items.map { it.code } shouldContainExactly RatioCode.entries
        ratios.valueOf(RatioCode.ROE) shouldBe 12.0
        ratios.valueOf(RatioCode.PE).shouldBeNull()
        ratios.items.first { it.code == RatioCode.ROE }.unit shouldBe RatioUnit.PERCENT
    }
})
