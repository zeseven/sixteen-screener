package com.sixteenscreener.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device storage of the active profile.
 */
interface ProfileStore {
    /** Stored profile, `null` when none is stored. */
    val profile: Flow<ActiveProfile?>

    /** Stores [profile], replacing the stored one. */
    suspend fun save(profile: ActiveProfile)

    /** Removes the stored profile if its ID is [profileId]; a profile stored in the meantime is kept. */
    suspend fun clear(profileId: ProfileId)
}

@Singleton
class DataStoreProfileStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProfileStore {

    override val profile: Flow<ActiveProfile?> = dataStore.data
        .map { preferences ->
            val id = preferences[PROFILE_ID]
            val name = preferences[PROFILE_NAME]
            if (id != null && name != null) ActiveProfile(ProfileId(id), name) else null
        }
        .distinctUntilChanged()

    override suspend fun save(profile: ActiveProfile) {
        dataStore.edit { preferences ->
            preferences[PROFILE_ID] = profile.id.value
            preferences[PROFILE_NAME] = profile.name
        }
    }

    override suspend fun clear(profileId: ProfileId) {
        dataStore.edit { preferences ->
            if (preferences[PROFILE_ID] == profileId.value) {
                preferences.remove(PROFILE_ID)
                preferences.remove(PROFILE_NAME)
            }
        }
    }

    private companion object {
        val PROFILE_ID = stringPreferencesKey("profile_id")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
    }
}
