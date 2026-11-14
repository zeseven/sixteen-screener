package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.di.ApplicationScope
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Sources offering balance sheets, the profile's default source first. Loads the sources when none are cached and falls back
     * to [FALLBACK_BALANCE_SHEET_SOURCES] when that load fails.
     */
    suspend fun getBalanceSheetSources(): List<Source>

    companion object {
        /** Balance sheet sources of the alpha backend, used while the source list cannot be loaded. */
        val FALLBACK_BALANCE_SHEET_SOURCES: List<Source> = listOf(
            fallbackSource(SourceId.FINNHUB, "Finnhub", isDefault = true),
            fallbackSource(SourceId.SEC_EDGAR, "SEC EDGAR", isDefault = false),
        )

        private fun fallbackSource(id: SourceId, name: String, isDefault: Boolean) = Source(
            id = id,
            name = name,
            capabilities = listOf(Capability.BALANCE_SHEET),
            selectableAsDefault = isDefault,
            isDefault = isDefault,
            limits = emptyList(),
            lastCall = null,
        )
    }
}

@Singleton
class DefaultSourceRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) : SourceRepository {

    private val cached = MutableStateFlow<List<Source>?>(null)
    private val lazyLoadMutex = Mutex()

    override val sources: StateFlow<List<Source>?> = cached.asStateFlow()

    init {
        profileRepository.onActiveProfileChange(applicationScope) { cached.value = null }
    }

    override suspend fun refresh(): ApiResult<List<Source>> = profileRepository.withActiveProfile { profileId ->
        apiClient.getSources(profileId)
            .clearProfileIfNotFound(profileRepository, profileId)
            .also { result -> if (result is ApiResult.Success && isActive(profileId)) cached.value = result.value }
    }

    override suspend fun getBalanceSheetSources(): List<Source> {
        val sources = cached.value
            ?: lazyLoadMutex.withLock { cached.value ?: refresh().getOrNull() }
            ?: return SourceRepository.FALLBACK_BALANCE_SHEET_SOURCES
        return sources
            .filter { Capability.BALANCE_SHEET in it.capabilities }
            .sortedByDescending { it.isDefault }
    }

    private suspend fun isActive(profileId: ProfileId) = profileRepository.activeProfileId() == profileId
}
