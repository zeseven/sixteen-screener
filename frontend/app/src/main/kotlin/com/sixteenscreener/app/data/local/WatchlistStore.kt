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
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
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
    @Serializable(with = StoredTagsSerializer::class)
    val tags: List<CachedTag> = emptyList(),
)

@Serializable
internal data class CachedTag(
    val icon: String,
    val text: String,
)

/** Reads only tags made of an icon and a text, so plain text tags stored by an older app version are dropped. */
internal object StoredTagsSerializer : JsonTransformingSerializer<List<CachedTag>>(ListSerializer(CachedTag.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonArray((element as? JsonArray).orEmpty().filter { it.isStoredTag() })

    private fun JsonElement.isStoredTag(): Boolean =
        this is JsonObject && listOf("icon", "text").all { (get(it) as? JsonPrimitive)?.isString == true }
}

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
    tags = tags.map { CachedTag(it.icon, it.text) },
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
    tags = tags.map { Tag(it.icon, it.text) },
)
