package com.sixteenscreener.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.domain.ProfileName
import com.sixteenscreener.app.domain.ProfileNameValidation
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reason the profile could not be selected. */
sealed interface ProfileError {
    data object NameBlank : ProfileError

    data object NameTooLong : ProfileError

    data class Request(val error: ApiError) : ProfileError
}

data class ProfileUiState(
    val name: String = "",
    val isSubmitting: Boolean = false,
    val error: ProfileError? = null,
)

/**
 * First start profile prompt. A successful selection is stored by [ProfileRepository]; navigation follows the stored profile.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = mutableUiState.asStateFlow()

    fun onNameChange(name: String) {
        mutableUiState.update { it.copy(name = name, error = null) }
    }

    fun onContinue() {
        val state = mutableUiState.value
        if (state.isSubmitting) return
        when (val validation = ProfileName.parse(state.name)) {
            ProfileNameValidation.Blank -> mutableUiState.update { it.copy(error = ProfileError.NameBlank) }
            ProfileNameValidation.TooLong -> mutableUiState.update { it.copy(error = ProfileError.NameTooLong) }
            is ProfileNameValidation.Valid -> select(validation.name)
        }
    }

    private fun select(name: ProfileName) {
        mutableUiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = profileRepository.selectProfile(name)
            mutableUiState.update {
                it.copy(isSubmitting = false, error = (result as? ApiResult.Failure)?.let { failure -> ProfileError.Request(failure.error) })
            }
        }
    }
}
