package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.Tag

/**
 * User tags of watchlist companies (ADR-061, ADR-086). Tags are stored with the watchlist entries, so reading and
 * changing them makes no source requests.
 */
interface TagsUseCase {
    /**
     * Replaces all tags of [symbol] in profile [profileId] with [tags] and returns the stored tags in request order; an
     * empty list removes all tags.
     *
     * Throws [com.sixteenscreener.domain.model.InvalidInputException] when there are more than
     * [Tag.MAX_TAGS_PER_COMPANY] tags, two tags are the same tag or [symbol] is not in the watchlist, and
     * [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun setCompanyTags(profileId: ProfileId, symbol: Symbol, tags: List<Tag>): List<Tag>

    /**
     * All distinct tags of the watchlist companies of profile [profileId]: of the same tags the first found in
     * watchlist order is returned. Sorted by [Tag.DISPLAY_ORDER].
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun profileTags(profileId: ProfileId): List<Tag>
}
