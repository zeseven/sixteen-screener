package com.sixteenscreener.domain.model

import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Backend-generated profile identifier (ADR-025).
 */
@JvmInline
value class ProfileId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        /** New random profile ID. */
        fun random(): ProfileId = ProfileId(UUID.randomUUID())

        /** Parses [raw] as a profile ID, throwing [InvalidInputException] when it is not a UUID. */
        fun of(raw: String): ProfileId =
            parseOrNull(raw) ?: throw InvalidInputException("Invalid profile ID '$raw'")

        /** Parses [raw] as a profile ID, returning null when it is not a UUID in canonical form. */
        fun parseOrNull(raw: String): ProfileId? =
            runCatching { UUID.fromString(raw) }.getOrNull()
                ?.takeIf { it.toString().equals(raw, ignoreCase = true) }
                ?.let(::ProfileId)
    }
}

/**
 * Profile name: any characters, trimmed, 1 to [MAX_LENGTH] characters (ADR-025).
 */
@JvmInline
value class ProfileName private constructor(val value: String) {

    /** Key used to compare names: Unicode NFC, lower case (`Locale.ROOT`). */
    val normalized: String
        get() = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    override fun toString(): String = value

    companion object {
        /** Maximum length in characters (Unicode code points) after trimming. */
        const val MAX_LENGTH = 40

        /** Trims [raw] and validates it, throwing [InvalidInputException] when blank or too long. */
        fun of(raw: String): ProfileName {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) throw InvalidInputException("Profile name must not be blank")
            if (trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH) {
                throw InvalidInputException("Profile name must not be longer than $MAX_LENGTH characters")
            }
            return ProfileName(trimmed)
        }
    }
}

/**
 * User profile with its watchlist.
 *
 * @property id backend-generated ID
 * @property name name as first entered, trimmed
 * @property defaultSource default source chosen by the user; null means the configured default source
 * @property watchlist watchlist entries in insertion order
 * @property createdAt creation time
 * @property updatedAt last modification time
 */
data class Profile(
    val id: ProfileId,
    val name: ProfileName,
    val defaultSource: SourceId?,
    val watchlist: List<WatchlistEntry>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Symbols of the watchlist in insertion order. */
    val watchlistSymbols: List<Symbol> get() = watchlist.map { it.symbol }

    /** Watchlist entry of [symbol], or null when [symbol] is not in the watchlist. */
    fun watchlistEntry(symbol: Symbol): WatchlistEntry? = watchlist.firstOrNull { it.symbol == symbol }

    companion object {
        /** Maximum number of watchlist entries (spec `maxItems`). */
        const val MAX_WATCHLIST_SIZE = 250
    }
}
