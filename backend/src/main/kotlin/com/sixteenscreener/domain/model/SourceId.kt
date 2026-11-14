package com.sixteenscreener.domain.model

/**
 * Configured source identifier, e.g. `finnhub`, `sec-edgar`, `logoapi`. Matches the `SourceId` pattern of the API spec.
 */
@JvmInline
value class SourceId private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[a-z0-9\\-]{1,30}$")

        /** Parses [raw] as a source ID, throwing [InvalidInputException] when it doesn't match the spec pattern. */
        fun of(raw: String): SourceId =
            parseOrNull(raw) ?: throw InvalidInputException("Invalid source ID '$raw'")

        /** Parses [raw] as a source ID, returning null when it doesn't match the spec pattern. */
        fun parseOrNull(raw: String): SourceId? = raw.takeIf(PATTERN::matches)?.let(::SourceId)
    }
}
