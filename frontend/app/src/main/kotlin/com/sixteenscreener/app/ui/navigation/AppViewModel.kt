package com.sixteenscreener.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides between the profile prompt and the main screen from the stored profile.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    profileRepository: ProfileRepository,
) : ViewModel() {

    /** Whether a profile is stored; `null` until the device storage has been read. */
    val hasProfile: StateFlow<Boolean?> = profileRepository.currentProfile
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
