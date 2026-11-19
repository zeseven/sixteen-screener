package com.sixteenscreener.domain.model

/**
 * What caused a company data request (ADR-083).
 */
enum class RequestTrigger {
    /** Anything but the refresh button: opening the detailed screen, watchlist rows, adding a company to the watchlist. */
    AUTO,

    /** The refresh button of the detailed screen. */
    MANUAL,
    ;

    companion object {
        /** Parses [raw] as a trigger name, throwing [InvalidInputException] for anything but `AUTO` and `MANUAL`. */
        fun of(raw: String): RequestTrigger =
            entries.firstOrNull { it.name == raw }
                ?: throw InvalidInputException("Invalid trigger '$raw', expected ${entries.joinToString(" or ")}")
    }
}
