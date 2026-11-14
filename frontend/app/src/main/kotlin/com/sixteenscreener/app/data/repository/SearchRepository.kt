package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.SearchResults
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Company search in the default source of the active profile.
 */
interface SearchRepository {
    /** Searches companies by ticker or name with `GET /search`; cancelling the caller cancels the request. */
    suspend fun search(query: String): ApiResult<SearchResults>
}

@Singleton
class DefaultSearchRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
) : SearchRepository {

    override suspend fun search(query: String): ApiResult<SearchResults> =
        profileRepository.withActiveProfile { profileId -> apiClient.search(profileId, query) }
}
