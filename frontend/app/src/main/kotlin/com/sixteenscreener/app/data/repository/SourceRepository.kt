package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.di.ApplicationScope
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configured data sources of the active profile, cached in memory until the next refresh or a profile change.
 */
interface SourceRepository {
    /** Sources from the last successful load, `null` until loaded for the active profile. */
    val sources: StateFlow<List<Source>?>

    /** Loads the sources with `GET /sources`; the cached list is kept when the call fails. */
    suspend fun refresh(): ApiResult<List<Source>>
}

@Singleton
class DefaultSourceRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) : SourceRepository {

    private val cached = MutableStateFlow<List<Source>?>(null)

    override val sources: StateFlow<List<Source>?> = cached.asStateFlow()

    init {
        profileRepository.onActiveProfileChange(applicationScope) { cached.value = null }
    }

    override suspend fun refresh(): ApiResult<List<Source>> = profileRepository.withActiveProfile { profileId ->
        apiClient.getSources(profileId)
            .clearProfileIfNotFound(profileRepository, profileId)
            .also { result -> if (result is ApiResult.Success && isActive(profileId)) cached.value = result.value }
    }

    private suspend fun isActive(profileId: ProfileId) = profileRepository.activeProfileId() == profileId
}
