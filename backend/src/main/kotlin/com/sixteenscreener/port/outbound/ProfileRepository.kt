package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Symbol
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

    /** The symbol was not added because the watchlist already holds [Profile.MAX_WATCHLIST_SIZE] symbols. */
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
     * Appends [symbol] to the watchlist of profile [id] unless already present (idempotent), setting `updatedAt` to [at].
     * Returns [WatchlistUpdate.WatchlistFull] when the symbol is absent and the watchlist is full.
     */
    suspend fun addToWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate

    /** Removes [symbol] from the watchlist of profile [id] if present (idempotent), setting `updatedAt` to [at]. */
    suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate
}
