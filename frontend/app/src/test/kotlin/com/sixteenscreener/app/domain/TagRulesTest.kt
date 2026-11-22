package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRulesTest {

    private val king = Tag("chess-king", "Dividend king")
    private val coin = Tag("coin", "Income")

    private fun tags(count: Int) = (1..count).map { Tag("star", "Tag $it") }

    @Test
    fun `tag with an icon and a text can be added`() {
        assertNull(TagRules.problemOfAdding(listOf(king), "coin", "Income"))
    }

    @Test
    fun `tag without an icon cannot be added`() {
        assertEquals(TagProblem.ICON_MISSING, TagRules.problemOfAdding(emptyList(), null, "Income"))
    }

    @Test
    fun `empty or blank text cannot be added`() {
        assertEquals(TagProblem.TEXT_BLANK, TagRules.problemOfAdding(emptyList(), "coin", ""))
        assertEquals(TagProblem.TEXT_BLANK, TagRules.problemOfAdding(emptyList(), "coin", " \t "))
    }

    @Test
    fun `text of 100 characters after trimming can be added, 101 cannot`() {
        assertNull(TagRules.problemOfAdding(emptyList(), "coin", "  ${"a".repeat(100)}  "))
        assertEquals(TagProblem.TEXT_TOO_LONG, TagRules.problemOfAdding(emptyList(), "coin", "a".repeat(101)))
    }

    @Test
    fun `characters outside the basic plane count once`() {
        assertNull(TagRules.problemOfAdding(emptyList(), "coin", "💰".repeat(100)))
    }

    @Test
    fun `same icon and text ignoring case and surrounding spaces is a duplicate`() {
        assertEquals(TagProblem.DUPLICATE, TagRules.problemOfAdding(listOf(king), "chess-king", "  DIVIDEND KING "))
    }

    @Test
    fun `same text with another icon is not a duplicate`() {
        assertNull(TagRules.problemOfAdding(listOf(king), "crown", "Dividend king"))
    }

    @Test
    fun `21st tag cannot be added, the 20th can`() {
        assertNull(TagRules.problemOfAdding(tags(19), "coin", "Income"))
        assertEquals(TagProblem.LIMIT_REACHED, TagRules.problemOfAdding(tags(20), "coin", "Income"))
    }

    @Test
    fun `reached limit is reported before missing fields`() {
        assertEquals(TagProblem.LIMIT_REACHED, TagRules.problemOfAdding(tags(20), null, ""))
    }

    @Test
    fun `limitText keeps short texts and cuts long ones to 100 characters`() {
        assertEquals("abc", TagRules.limitText("abc"))
        assertEquals("a".repeat(100), TagRules.limitText("a".repeat(150)))
    }

    @Test
    fun `limitText never splits a character outside the basic plane`() {
        val text = "💰".repeat(101)

        assertEquals("💰".repeat(100), TagRules.limitText(text))
    }

    @Test
    fun `contains compares tags ignoring the case of the text`() {
        assertTrue(TagRules.contains(listOf(king, coin), Tag("coin", "INCOME")))
        assertFalse(TagRules.contains(listOf(king, coin), Tag("cash", "Income")))
        assertFalse(TagRules.contains(emptyList(), coin))
    }

    @Test
    fun `distinctSorted keeps the first of same tags and sorts by text ignoring case, then by icon`() {
        val tags = listOf(Tag("coin", "income"), Tag("star", "Core"), Tag("coin", "Income"), Tag("cash", "Income"), Tag("gem", "apple"))

        assertEquals(
            listOf(Tag("gem", "apple"), Tag("star", "Core"), Tag("cash", "Income"), Tag("coin", "income")),
            TagRules.distinctSorted(tags),
        )
    }

    @Test
    fun `distinctSorted of no tags is empty`() {
        assertTrue(TagRules.distinctSorted(emptyList()).isEmpty())
    }
}
