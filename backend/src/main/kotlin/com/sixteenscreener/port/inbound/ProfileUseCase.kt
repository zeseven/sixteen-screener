package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem

/**
 * Profile as returned to the app.
 *
 * @property id backend-generated ID
 * @property name name as first entered, trimmed
 * @property defaultSources the profile's default source of each role: the configured defaults (ADR-083)
 * @property watchlist watchlist symbols in insertion order
 */
data class ProfileSettings(
    val id: ProfileId,
    val name: ProfileName,
    val defaultSources: DefaultSources,
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
     * Adds [symbol] to the watchlist of profile [id] and returns the stored watchlist item; idempotent (ADR-057, ADR-062).
     *
     * A symbol already in the watchlist is returned unchanged without source requests and without the directory check
     * (ADR-041). A new symbol listed in the exchange directory is stored with the name from the directory. A new symbol
     * of an exchange the source has no symbol list for (not covered) and that is in the static exchange table is stored
     * with [searchResultName] (trimmed; blank counts as absent), otherwise with the ticker as name. A new symbol is
     * stored with the sector of the company profile from the profile's default source of the company's role (ADR-083):
     * the cached profile if not expired, otherwise the profile is requested within the source request limits as an
     * `AUTO` request (a source with automatic requests off is not called); the sector is null when the profile can't
     * be loaded.
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile or a new symbol that is
     * neither in the exchange directory nor on a not covered exchange of the static exchange table (including while
     * the exchange directory is temporarily unavailable), and [com.sixteenscreener.domain.model.InvalidInputException]
     * when the watchlist is full or [searchResultName] is longer than
     * [com.sixteenscreener.domain.model.WatchlistEntry.MAX_NAME_LENGTH] characters.
     */
    suspend fun addToWatchlist(id: ProfileId, symbol: Symbol, searchResultName: String?): WatchlistItem

    /**
     * Removes [symbol] with its tags from the watchlist of profile [id]; idempotent.
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol)
}
