package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.local.ProfileStore
import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.ProfileName
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.map
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Active profile: selection by name and the stored profile ID.
 */
interface ProfileRepository {
    /** Profile stored on the device, `null` when no profile is selected yet or the stored one was removed. */
    val currentProfile: Flow<ActiveProfile?>

    /** Gets or creates the profile named [name] on the backend and stores it as the active profile on success. */
    suspend fun selectProfile(name: ProfileName): ApiResult<ActiveProfile>

    /**
     * Clears the stored profile if it is still [profileId]. Call it only when a profile-only endpoint
     * (`GET /profiles/{id}`, `GET /profiles/{id}/watchlist`, `GET /profiles/{id}/sources`) answered `404`:
     * on company endpoints a `404` can mean an unknown symbol.
     */
    suspend fun onProfileNotFound(profileId: ProfileId)
}

/**
 * Returns this result unchanged, clearing the stored profile first when it is a `404` of a profile-only endpoint called with [profileId].
 */
suspend fun <T> ApiResult<T>.clearProfileIfNotFound(
    profileRepository: ProfileRepository,
    profileId: ProfileId,
): ApiResult<T> = also {
    if (this is ApiResult.Failure && error is ApiError.NotFound) {
        profileRepository.onProfileNotFound(profileId)
    }
}

@Singleton
class DefaultProfileRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileStore: ProfileStore,
) : ProfileRepository {

    override val currentProfile: Flow<ActiveProfile?> = profileStore.profile

    override suspend fun selectProfile(name: ProfileName): ApiResult<ActiveProfile> =
        apiClient.getOrCreateProfile(name.value)
            .map { ActiveProfile(it.id, it.name) }
            .also { result -> if (result is ApiResult.Success) profileStore.save(result.value) }

    override suspend fun onProfileNotFound(profileId: ProfileId) = profileStore.clear(profileId)
}
