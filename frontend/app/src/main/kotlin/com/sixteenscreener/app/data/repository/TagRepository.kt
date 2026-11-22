package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User tags of the active profile (ADR-061, ADR-086). Tags are stored in the watchlist entries, so the watchlist held in memory gets
 * the tags of a company as soon as the backend stored them.
 */
interface TagRepository {
    /** Distinct tags used by the profile, offered for reuse when a tag is added. */
    suspend fun getProfileTags(): ApiResult<List<Tag>>

    /** Replaces all tags of the watchlist company [symbol] with [tags]; returns the stored tags. */
    suspend fun setCompanyTags(symbol: Symbol, tags: List<Tag>): ApiResult<List<Tag>>
}

@Singleton
class DefaultTagRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    private val watchlistRepository: WatchlistRepository,
) : TagRepository {

    override suspend fun getProfileTags(): ApiResult<List<Tag>> = profileRepository.withActiveProfile { profileId ->
        apiClient.getProfileTags(profileId).clearProfileIfNotFound(profileRepository, profileId)
    }

    override suspend fun setCompanyTags(symbol: Symbol, tags: List<Tag>): ApiResult<List<Tag>> =
        profileRepository.withActiveProfile { profileId ->
            apiClient.setCompanyTags(profileId, symbol, tags)
                .clearProfileIfNotFound(profileRepository, profileId)
                .also { result -> if (result is ApiResult.Success) watchlistRepository.updateTags(profileId, symbol, result.value) }
        }
}
