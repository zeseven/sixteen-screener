package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.inbound.ProfileRegistration
import com.sixteenscreener.port.inbound.ProfileSettings
import com.sixteenscreener.port.inbound.ProfileUseCase
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SymbolDirectory
import com.sixteenscreener.port.outbound.WatchlistUpdate

class ProfileService(
    private val profiles: ProfileRepository,
    private val symbolDirectory: SymbolDirectory,
    private val catalog: SourceCatalog,
    private val clock: Clock,
) : ProfileUseCase {

    override suspend fun getOrCreate(rawName: String): ProfileRegistration {
        val name = ProfileName.of(rawName)
        val now = clock.now()
        val candidate = Profile(ProfileId.random(), name, defaultSource = null, emptyList(), createdAt = now, updatedAt = now)
        val creation = profiles.getOrCreate(candidate)
        return ProfileRegistration(settingsOf(creation.profile), creation.created)
    }

    override suspend fun profile(id: ProfileId): ProfileSettings = settingsOf(profiles.existing(id))

    override suspend fun addToWatchlist(id: ProfileId, symbol: Symbol) {
        val profile = profiles.existing(id)
        if (symbol in profile.watchlist) return
        symbolDirectory.find(symbol) ?: throw ResourceNotFoundException("Unknown symbol '$symbol'")
        when (profiles.addToWatchlist(id, symbol, clock.now())) {
            is WatchlistUpdate.Updated -> Unit
            WatchlistUpdate.ProfileNotFound -> throw profileNotFound(id)
            WatchlistUpdate.WatchlistFull ->
                throw InvalidInputException("Watchlist is full (${Profile.MAX_WATCHLIST_SIZE} companies)")
        }
    }

    override suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol) {
        if (profiles.removeFromWatchlist(id, symbol, clock.now()) == WatchlistUpdate.ProfileNotFound) {
            throw profileNotFound(id)
        }
    }

    private fun settingsOf(profile: Profile) = ProfileSettings(
        id = profile.id,
        name = profile.name,
        defaultSource = catalog.defaultSourceOf(profile),
        watchlist = profile.watchlist,
    )
}
