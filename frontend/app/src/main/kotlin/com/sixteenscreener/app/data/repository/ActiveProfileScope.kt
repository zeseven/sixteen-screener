package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** ID of the active profile, `null` when none is selected. */
suspend fun ProfileRepository.activeProfileId(): ProfileId? = currentProfile.first()?.id

/** Calls [request] with the active profile ID, or fails with [ApiError.NoProfile] when no profile is selected. */
suspend inline fun <T> ProfileRepository.withActiveProfile(request: (ProfileId) -> ApiResult<T>): ApiResult<T> =
    activeProfileId()?.let { request(it) } ?: ApiResult.Failure(ApiError.NoProfile)

/**
 * Calls [onChange] in [scope] every time the active profile changes after the first observed value,
 * so repositories drop in-memory state of the previous profile.
 */
fun ProfileRepository.onActiveProfileChange(scope: CoroutineScope, onChange: () -> Unit): Job = scope.launch {
    currentProfile
        .map { it?.id }
        .distinctUntilChanged()
        .drop(1)
        .collect { onChange() }
}
