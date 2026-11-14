package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.ProfileCreation
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.WatchlistUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class InMemoryProfileRepository : ProfileRepository {
    private val mutex = Mutex()
    private val profiles = mutableMapOf<ProfileId, Profile>()
    private val idsByNormalizedName = mutableMapOf<String, ProfileId>()

    override suspend fun findById(id: ProfileId): Profile? = mutex.withLock { profiles[id] }

    override suspend fun getOrCreate(candidate: Profile): ProfileCreation = mutex.withLock {
        val existing = idsByNormalizedName[candidate.name.normalized]?.let(profiles::getValue)
        if (existing != null) {
            ProfileCreation(existing, created = false)
        } else {
            profiles[candidate.id] = candidate
            idsByNormalizedName[candidate.name.normalized] = candidate.id
            ProfileCreation(candidate, created = true)
        }
    }

    override suspend fun addToWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate =
        update(id) { profile ->
            when {
                symbol in profile.watchlist -> profile.copy(updatedAt = at)
                profile.watchlist.size >= Profile.MAX_WATCHLIST_SIZE -> null
                else -> profile.copy(watchlist = profile.watchlist + symbol, updatedAt = at)
            }
        }

    override suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate =
        update(id) { profile -> profile.copy(watchlist = profile.watchlist - symbol, updatedAt = at) }

    fun save(profile: Profile) {
        profiles[profile.id] = profile
        idsByNormalizedName[profile.name.normalized] = profile.id
    }

    private suspend fun update(id: ProfileId, change: (Profile) -> Profile?): WatchlistUpdate = mutex.withLock {
        val profile = profiles[id] ?: return@withLock WatchlistUpdate.ProfileNotFound
        val updated = change(profile) ?: return@withLock WatchlistUpdate.WatchlistFull
        profiles[id] = updated
        WatchlistUpdate.Updated(updated)
    }
}
