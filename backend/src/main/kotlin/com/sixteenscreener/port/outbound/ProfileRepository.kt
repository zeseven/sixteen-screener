package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry
import java.time.Instant

/**
 * Result of [ProfileRepository.getOrCreate].
 *
 * @property profile the existing or the newly created profile
 * @property created whether the profile was created by this call
 */
data class ProfileCreation(
    val profile: Profile,
    val created: Boolean,
)

/**
 * Result of a watchlist change.
 */
sealed interface WatchlistUpdate {
    /**
     * The change was applied, or the watchlist already was in the requested state.
     *
     * @property profile profile after the change
     */
    data class Updated(val profile: Profile) : WatchlistUpdate

    /** No profile with the given ID exists. */
    data object ProfileNotFound : WatchlistUpdate

    /** The entry was not added because the watchlist already holds [Profile.MAX_WATCHLIST_SIZE] entries. */
    data object WatchlistFull : WatchlistUpdate
}

/**
 * Storage of user profiles (`profiles` and `profileNames` collections, ADR-025).
 */
interface ProfileRepository {
    /** Profile with [id], or null when it doesn't exist. */
    suspend fun findById(id: ProfileId): Profile?

    /**
     * Returns the profile whose name equals [candidate]'s name after normalization ([ProfileName.normalized]),
     * or stores [candidate] when there is none. Runs in one transaction, so concurrent calls with the same name
     * return the same profile and exactly one of them reports `created = true`.
     */
    suspend fun getOrCreate(candidate: Profile): ProfileCreation

    /**
     * Appends [entry] to the watchlist of profile [id], setting `updatedAt` to [at]. When the watchlist already holds an
     * entry with the same symbol, that entry is kept unchanged (idempotent).
     * Returns [WatchlistUpdate.WatchlistFull] when the symbol is absent and the watchlist is full.
     */
    suspend fun addToWatchlist(id: ProfileId, entry: WatchlistEntry, at: Instant): WatchlistUpdate

    /**
     * Sets the name and the sector of the watchlist entry of [symbol] in profile [id] to [name] and [sector], setting
     * `updatedAt` to [at]. Leaves the profile unchanged when [symbol] is not in the watchlist or its name and sector
     * already equal [name] and [sector].
     */
    suspend fun updateWatchlistCompany(id: ProfileId, symbol: Symbol, name: String, sector: String?, at: Instant): WatchlistUpdate

    /**
     * Removes the watchlist entry of [symbol], with its tags, from profile [id] if present (idempotent),
     * setting `updatedAt` to [at].
     */
    suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate

    /**
     * Applies [change] to the indicator settings of profile [id] in one transaction (ADR-072). The result is stored, with
     * `updatedAt` set to [at], only when it differs from the stored settings. [change] may be called more than once and
     * must have no side effects. Returns the profile after the change, or null when it doesn't exist.
     */
    suspend fun changeIndicatorSettings(
        id: ProfileId,
        at: Instant,
        change: (List<IndicatorSetting>) -> List<IndicatorSetting>,
    ): Profile?
}
