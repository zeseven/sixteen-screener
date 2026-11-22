package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TagTest : FunSpec({

    context("Tag.of") {
        test("trims the text and keeps the icon") {
            Tag.of("chess-king", "  Dividend king \t") shouldBe Tag("chess-king", "Dividend king")
        }

        test("accepts a text of exactly 100 characters after trimming") {
            val text = "x".repeat(Tag.MAX_TEXT_LENGTH)

            Tag.of("star", " $text ").text shouldBe text
        }

        test("counts characters as Unicode code points") {
            val text = "😀".repeat(Tag.MAX_TEXT_LENGTH)

            Tag.of("star", text).text shouldBe text
        }

        test("rejects a text of 101 characters") {
            shouldThrow<InvalidInputException> { Tag.of("star", "x".repeat(Tag.MAX_TEXT_LENGTH + 1)) }
        }

        context("rejects a text that is empty after trimming") {
            withData(nameFn = { "'$it'" }, "", " ", "\t\n ") { text ->
                shouldThrow<InvalidInputException> { Tag.of("star", text) }
            }
        }

        context("accepts icons matching the spec pattern") {
            withData("a", "chess-king", "piggy-bank", "0", "-", "a".repeat(40)) { icon ->
                Tag.of(icon, "text").icon shouldBe icon
            }
        }

        context("rejects icons not matching the spec pattern") {
            withData(nameFn = { "'$it'" }, "", "Chess-king", "chess_king", "chess king", " star", "star ", "a".repeat(41), "ä") { icon ->
                shouldThrow<InvalidInputException> { Tag.of(icon, "text") }
            }
        }
    }

    context("Tag.parseOrNull") {
        test("trims the text of a valid tag") {
            Tag.parseOrNull("star", " core ") shouldBe Tag("star", "core")
        }

        withData(nameFn = { "rejects '${it.first}' / '${it.second}'" }, "Bad" to "text", "star" to " ", "star" to "x".repeat(101)) {
            (icon, text) ->
            Tag.parseOrNull(icon, text).shouldBeNull()
        }
    }

    context("same tag") {
        test("tags with equal icons and texts differing only in case have the same key") {
            Tag("star", "Dividend King").key shouldBe Tag("star", "dIVIDEND kING").key
        }

        test("tags with different icons are different tags") {
            Tag("star", "core").key shouldNotBe Tag("gem", "core").key
        }
    }

    context("requireValidCompanyTags") {
        test("accepts no tags") {
            shouldNotThrowAny { Tag.requireValidCompanyTags(emptyList()) }
        }

        test("accepts 20 distinct tags") {
            shouldNotThrowAny { Tag.requireValidCompanyTags((1..Tag.MAX_TAGS_PER_COMPANY).map { Tag("star", "t$it") }) }
        }

        test("rejects 21 tags") {
            shouldThrow<InvalidInputException> {
                Tag.requireValidCompanyTags((1..Tag.MAX_TAGS_PER_COMPANY + 1).map { Tag("star", "t$it") })
            }
        }

        test("rejects two tags with equal icons and texts equal ignoring case") {
            shouldThrow<InvalidInputException> {
                Tag.requireValidCompanyTags(listOf(Tag("star", "Core"), Tag("gem", "x"), Tag("star", "cORE")))
            }
        }

        test("accepts equal texts with different icons") {
            shouldNotThrowAny { Tag.requireValidCompanyTags(listOf(Tag("star", "core"), Tag("gem", "Core"))) }
        }
    }

    test("display order is by text ignoring case, then by icon") {
        val tags = listOf(Tag("star", "beta"), Tag("gem", "Beta"), Tag("star", "Alpha"), Tag("bank", "alpha"), Tag("star", "gamma"))

        tags.sortedWith(Tag.DISPLAY_ORDER) shouldContainExactly listOf(
            Tag("bank", "alpha"),
            Tag("star", "Alpha"),
            Tag("gem", "Beta"),
            Tag("star", "beta"),
            Tag("star", "gamma"),
        )
    }
})
