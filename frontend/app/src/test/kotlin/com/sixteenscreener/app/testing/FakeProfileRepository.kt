package com.sixteenscreener.app.testing

import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.domain.ProfileName
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProfileRepository(initial: ActiveProfile? = null) : ProfileRepository {

    override val currentProfile = MutableStateFlow(initial)

    val selectedNames = mutableListOf<String>()

    var selectResult: suspend (ProfileName) -> ApiResult<ActiveProfile> = { name ->
        ApiResult.Success(ActiveProfile(ProfileId("id-${name.value}"), name.value))
    }

    override suspend fun selectProfile(name: ProfileName): ApiResult<ActiveProfile> {
        selectedNames += name.value
        return selectResult(name).also { if (it is ApiResult.Success) currentProfile.value = it.value }
    }

    override suspend fun onProfileNotFound(profileId: ProfileId) {
        if (currentProfile.value?.id == profileId) currentProfile.value = null
    }
}
