package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem

/**
 * Profile as returned to the app.
 *
 * @property id backend-generated ID
 * @property name name as first entered, trimmed
 * @property defaultSource the profile's default source, or the configured default source when the profile has none
 * @property watchlist watchlist symbols in insertion order
 */
data class ProfileSettings(
    val id: ProfileId,
    val name: ProfileName,
    val defaultSource: SourceId,
    val watchlist: List<Symbol>,
)

/**
 * Result of [ProfileUseCase.getOrCreate].
 *
 * @property profile the existing or the newly created profile
 * @property created whether the profile was created by this call
 */
data class ProfileRegistration(
    val profile: ProfileSettings,
    val created: Boolean,
)

/**
 * Profiles and their watchlists (ADR-025).
 */
interface ProfileUseCase {
    /**
     * Returns the profile with the name [rawName] (compared trimmed and case-insensitively) or creates it.
     * Throws [com.sixteenscreener.domain.model.InvalidInputException] when the name is blank or too long after trimming.
     */
    suspend fun getOrCreate(rawName: String): ProfileRegistration

    /** Profile with [id]; throws [com.sixteenscreener.domain.model.ResourceNotFoundException] when it doesn't exist. */
    suspend fun profile(id: ProfileId): ProfileSettings

    /**
     * Adds [symbol] to the watchlist of profile [id] and returns the stored watchlist item; idempotent (ADR-057).
     *
     * A symbol already in the watchlist is returned unchanged without source requests and without the directory check
     * (ADR-041). A new symbol is stored with the sector of the company profile from the profile's default source: the
     * cached profile if not expired, otherwise the profile is requested within the source request limits; the sector
     * is null when the profile can't be loaded.
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile or a new symbol that is
     * not in the exchange directory, and [com.sixteenscreener.domain.model.InvalidInputException] when the watchlist is full.
     */
    suspend fun addToWatchlist(id: ProfileId, symbol: Symbol): WatchlistItem

    /**
     * Removes [symbol] with its tags from the watchlist of profile [id]; idempotent.
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol)
}
