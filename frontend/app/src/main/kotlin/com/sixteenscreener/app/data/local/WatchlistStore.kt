package com.sixteenscreener.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sixteenscreener.app.data.remote.dto.toMarket
import com.sixteenscreener.app.di.WatchlistCache
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.WatchlistItem
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device storage of the watchlist entries of each profile, without market data (ADR-076).
 */
interface WatchlistStore {
    /** Stored watchlist of [profileId], `null` when none is stored or the stored data cannot be read. */
    suspend fun read(profileId: ProfileId): List<WatchlistItem>?

    /** Stores [items] as the watchlist of [profileId], replacing the stored one; returns `false` when the file cannot be written. */
    suspend fun save(profileId: ProfileId, items: List<WatchlistItem>): Boolean

    /** Removes the stored watchlist of [profileId], keeping those of other profiles; returns `false` when the file cannot be written. */
    suspend fun clear(profileId: ProfileId): Boolean
}

@Singleton
class DataStoreWatchlistStore @Inject constructor(
    @param:WatchlistCache private val dataStore: DataStore<Preferences>,
) : WatchlistStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(profileId: ProfileId): List<WatchlistItem>? {
        val stored = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()[keyOf(profileId)]
            ?: return null
        return try {
            json.decodeFromString<List<CachedWatchlistItem>>(stored).map { it.toDomain() }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(profileId: ProfileId, items: List<WatchlistItem>): Boolean {
        val encoded = json.encodeToString(items.map { it.toCached() })
        return edit { preferences -> preferences[keyOf(profileId)] = encoded }
    }

    override suspend fun clear(profileId: ProfileId): Boolean = edit { preferences -> preferences.remove(keyOf(profileId)) }

    private suspend fun edit(transform: suspend (MutablePreferences) -> Unit): Boolean = try {
        dataStore.edit(transform)
        true
    } catch (_: IOException) {
        false
    }

    private fun keyOf(profileId: ProfileId) = stringPreferencesKey(profileId.value)
}

@Serializable
internal data class CachedWatchlistItem(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String?,
    val type: String? = null,
    val stock: Boolean = true,
    val logoUrl: String,
    val sector: String?,
    val tags: List<String>,
)

internal fun WatchlistItem.toCached() = CachedWatchlistItem(
    symbol = symbol.value,
    ticker = ticker,
    exchange = exchange,
    name = name,
    market = market?.name,
    type = type,
    stock = stock,
    logoUrl = logoUrl,
    sector = sector,
    tags = tags,
)

internal fun CachedWatchlistItem.toDomain() = WatchlistItem(
    symbol = Symbol(symbol),
    ticker = ticker,
    exchange = exchange,
    name = name,
    market = market.toMarket(),
    type = type,
    stock = stock,
    logoUrl = logoUrl,
    sector = sector,
    tags = tags,
)
