package com.sixteenscreener.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.domain.ProfileName
import com.sixteenscreener.app.domain.ProfileNameValidation
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.AppInfo
import com.sixteenscreener.app.ui.profile.ProfileError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    /** Name of the active profile, `null` until read from the device. */
    val currentProfileName: String? = null,
    /** Name typed for the profile switch. */
    val newProfileName: String = "",
    val isSwitching: Boolean = false,
    val error: ProfileError? = null,
    /** The profile was switched; the screen navigates to the watchlist and reports it with [SettingsViewModel.onSwitchHandled]. */
    val isSwitched: Boolean = false,
    val appInfo: AppInfo,
)

/**
 * Settings screen: shows the active profile and app information and switches the profile by name.
 * A failed switch keeps the current profile; the screens of the main route reset themselves when the active profile changes.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    appInfo: AppInfo,
) : ViewModel() {

    private data class SwitchState(
        val newProfileName: String = "",
        val isSwitching: Boolean = false,
        val error: ProfileError? = null,
        val isSwitched: Boolean = false,
    )

    private val switchState = MutableStateFlow(SwitchState())

    val uiState: StateFlow<SettingsUiState> = combine(profileRepository.currentProfile, switchState) { profile, switch ->
        SettingsUiState(
            currentProfileName = profile?.name,
            newProfileName = switch.newProfileName,
            isSwitching = switch.isSwitching,
            error = switch.error,
            isSwitched = switch.isSwitched,
            appInfo = appInfo,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(appInfo = appInfo))

    fun onNewProfileNameChange(name: String) = switchState.update { it.copy(newProfileName = name, error = null) }

    fun onSwitchProfile() {
        val state = switchState.value
        if (state.isSwitching) return
        when (val validation = ProfileName.parse(state.newProfileName)) {
            ProfileNameValidation.Blank -> switchState.update { it.copy(error = ProfileError.NameBlank) }
            ProfileNameValidation.TooLong -> switchState.update { it.copy(error = ProfileError.NameTooLong) }
            is ProfileNameValidation.Valid -> switchTo(validation.name)
        }
    }

    fun onSwitchHandled() = switchState.update { it.copy(isSwitched = false) }

    private fun switchTo(name: ProfileName) {
        switchState.update { it.copy(isSwitching = true, error = null) }
        viewModelScope.launch {
            when (val result = profileRepository.selectProfile(name)) {
                is ApiResult.Success -> switchState.value = SwitchState(isSwitched = true)
                is ApiResult.Failure -> switchState.update { it.copy(isSwitching = false, error = ProfileError.Request(result.error)) }
            }
        }
    }
}
