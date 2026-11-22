package com.sixteenscreener.domain.model

import java.util.Locale

/**
 * User tag of a watchlist company: an icon from the app's predefined icon set and a text (ADR-061, ADR-086).
 *
 * @property icon ID of the icon in the app's icon set; not checked against the set
 * @property text trimmed text of 1 to [MAX_TEXT_LENGTH] characters
 */
data class Tag(
    val icon: String,
    val text: String,
) {
    /** Two tags with equal keys are the same tag: equal icons and texts equal ignoring case. */
    val key: Pair<String, String> get() = icon to text.lowercase(Locale.ROOT)

    companion object {
        /** Maximum length in characters (Unicode code points) of a tag text after trimming (spec `maxLength`). */
        const val MAX_TEXT_LENGTH = 100

        /** Maximum number of tags of one company. */
        const val MAX_TAGS_PER_COMPANY = 20

        private val ICON_PATTERN = Regex("^[a-z0-9-]{1,40}$")

        /** Order of the profile's tags: by text ignoring case, then by icon. */
        val DISPLAY_ORDER: Comparator<Tag> = compareBy<Tag>({ it.text.lowercase(Locale.ROOT) }, { it.icon })

        /** Trims [text] and validates both parts, throwing [InvalidInputException] when either is invalid. */
        fun of(icon: String, text: String): Tag =
            validationErrorOf(icon, text.trim())?.let { throw InvalidInputException(it) } ?: Tag(icon, text.trim())

        /** Tag of [icon] and the trimmed [text], or null when either is invalid. */
        fun parseOrNull(icon: String, text: String): Tag? =
            text.trim().takeIf { validationErrorOf(icon, it) == null }?.let { Tag(icon, it) }

        /**
         * Validates the tags of one company, throwing [InvalidInputException] when there are more than
         * [MAX_TAGS_PER_COMPANY] tags or two of them are the same tag.
         */
        fun requireValidCompanyTags(tags: List<Tag>) {
            if (tags.size > MAX_TAGS_PER_COMPANY) {
                throw InvalidInputException("A company must not have more than $MAX_TAGS_PER_COMPANY tags, got ${tags.size}")
            }
            tags.groupBy { it.key }.values.firstOrNull { it.size > 1 }?.let { duplicates ->
                throw InvalidInputException("Tag '${duplicates.first().text}' with icon '${duplicates.first().icon}' is repeated")
            }
        }

        private fun validationErrorOf(icon: String, trimmedText: String): String? = when {
            !ICON_PATTERN.matches(icon) -> "Invalid tag icon '$icon'"
            trimmedText.isEmpty() -> "Tag text must not be blank"
            trimmedText.codePointCount(0, trimmedText.length) > MAX_TEXT_LENGTH ->
                "Tag text must not be longer than $MAX_TEXT_LENGTH characters"
            else -> null
        }
    }
}
