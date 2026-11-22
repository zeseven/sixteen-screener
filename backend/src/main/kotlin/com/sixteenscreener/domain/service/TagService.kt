package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.Tag
import com.sixteenscreener.port.inbound.TagsUseCase
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.TagsUpdate

class TagService(
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : TagsUseCase {

    override suspend fun setCompanyTags(profileId: ProfileId, symbol: Symbol, tags: List<Tag>): List<Tag> {
        Tag.requireValidCompanyTags(tags)
        return when (val update = profiles.setWatchlistTags(profileId, symbol, tags, clock.now())) {
            is TagsUpdate.Updated -> update.profile.watchlistEntry(symbol)?.tags ?: tags
            TagsUpdate.ProfileNotFound -> throw profileNotFound(profileId)
            TagsUpdate.NotInWatchlist -> throw InvalidInputException("Company '$symbol' is not in the watchlist")
        }
    }

    override suspend fun profileTags(profileId: ProfileId): List<Tag> =
        profiles.existing(profileId).watchlist
            .flatMap { it.tags }
            .distinctBy { it.key }
            .sortedWith(Tag.DISPLAY_ORDER)
}
