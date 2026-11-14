package com.sixteenscreener.domain.model

import java.util.Locale

/**
 * Search query: a ticker or the first letters of a company name, trimmed, [MIN_LENGTH] to [MAX_LENGTH] characters.
 */
@JvmInline
value class SearchQuery private constructor(val value: String) {

    /** Key used to compare queries: lower case (`Locale.ROOT`). */
    val normalized: String
        get() = value.lowercase(Locale.ROOT)

    override fun toString(): String = value

    companion object {
        /** Minimum length in characters after trimming. */
        const val MIN_LENGTH = 3

        /** Maximum length in characters after trimming. */
        const val MAX_LENGTH = 50

        /** Trims [raw] and validates its length, throwing [InvalidInputException] when it is too short or too long. */
        fun of(raw: String): SearchQuery {
            val trimmed = raw.trim()
            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
                throw InvalidInputException("Search query must be $MIN_LENGTH to $MAX_LENGTH characters long")
            }
            return SearchQuery(trimmed)
        }
    }
}
