package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Tag
import java.util.Locale

/** Reason why a tag cannot be added to the tags of a company. */
enum class TagProblem { LIMIT_REACHED, ICON_MISSING, TEXT_BLANK, TEXT_TOO_LONG, DUPLICATE }

/**
 * Rules of user tags (ADR-086): an icon and a trimmed text of 1 to [MAX_TEXT_LENGTH] characters, at most [MAX_TAGS] tags per company
 * and no two tags that are the same tag.
 */
object TagRules {
    const val MAX_TAGS = 20
    const val MAX_TEXT_LENGTH = 100

    /** Problem of adding a tag with [icon] and [text] to [tags] of a company, `null` when it can be added. */
    fun problemOfAdding(tags: List<Tag>, icon: String?, text: String): TagProblem? {
        val trimmed = text.trim()
        return when {
            tags.size >= MAX_TAGS -> TagProblem.LIMIT_REACHED
            icon == null -> TagProblem.ICON_MISSING
            trimmed.isEmpty() -> TagProblem.TEXT_BLANK
            trimmed.characterCount() > MAX_TEXT_LENGTH -> TagProblem.TEXT_TOO_LONG
            contains(tags, Tag(icon, trimmed)) -> TagProblem.DUPLICATE
            else -> null
        }
    }

    /** [text] cut to [MAX_TEXT_LENGTH] characters, counting a character outside the basic plane once. */
    fun limitText(text: String): String =
        if (text.characterCount() <= MAX_TEXT_LENGTH) text else text.substring(0, text.offsetByCodePoints(0, MAX_TEXT_LENGTH))

    /** Whether [tags] contain a tag that is the same tag as [tag]. */
    fun contains(tags: List<Tag>, tag: Tag): Boolean = tags.any { it.isSameAs(tag) }

    /** [tags] without repeated same tags (the first one is kept), sorted by text ignoring case, then by icon. */
    fun distinctSorted(tags: List<Tag>): List<Tag> = tags
        .distinctBy { it.icon to it.text.uppercase(Locale.ROOT).lowercase(Locale.ROOT) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Tag::text).thenBy(Tag::icon).thenBy(Tag::text))

    private fun String.characterCount(): Int = codePointCount(0, length)
}
