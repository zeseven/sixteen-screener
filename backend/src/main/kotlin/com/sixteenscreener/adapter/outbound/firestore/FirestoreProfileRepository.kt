package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.port.outbound.ProfileCreation
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.WatchlistUpdate
import java.security.MessageDigest
import java.time.Instant

class FirestoreProfileRepository(private val firestore: Firestore) : ProfileRepository {
    private val profiles = firestore.collection(FirestoreCollections.PROFILES)
    private val profileNames = firestore.collection(FirestoreCollections.PROFILE_NAMES)

    override suspend fun findById(id: ProfileId): Profile? =
        profiles.document(id.toString()).get().await().toProfileOrNull()

    override suspend fun getOrCreate(candidate: Profile): ProfileCreation = firestore.transaction { tx ->
        val nameRef = profileNames.document(nameDocumentId(candidate.name))
        val existing = tx.get(nameRef).get().getString(PROFILE_ID)
            ?.let { tx.get(profiles.document(it)).get().toProfileOrNull() }
        if (existing != null) {
            ProfileCreation(existing, created = false)
        } else {
            tx.set(nameRef, mapOf(PROFILE_ID to candidate.id.toString()))
            tx.create(profiles.document(candidate.id.toString()), candidate.toDocument())
            ProfileCreation(candidate, created = true)
        }
    }

    override suspend fun addToWatchlist(id: ProfileId, entry: WatchlistEntry, at: Instant): WatchlistUpdate =
        changeWatchlist(id, at) { watchlist ->
            when {
                watchlist.any { it.symbol == entry.symbol } -> watchlist
                watchlist.size >= Profile.MAX_WATCHLIST_SIZE -> null
                else -> watchlist + entry
            }
        }

    override suspend fun updateWatchlistCompany(
        id: ProfileId,
        symbol: Symbol,
        name: String,
        sector: String?,
        at: Instant,
    ): WatchlistUpdate =
        firestore.transaction { tx ->
            val ref = profiles.document(id.toString())
            val profile = tx.get(ref).get().toProfileOrNull() ?: return@transaction WatchlistUpdate.ProfileNotFound
            val entry = profile.watchlistEntry(symbol)
            if (entry == null || (entry.name == name && entry.sector == sector)) {
                return@transaction WatchlistUpdate.Updated(profile)
            }
            val watchlist = profile.watchlist.map { if (it.symbol == symbol) it.copy(name = name, sector = sector) else it }
            tx.update(ref, mapOf(WATCHLIST to watchlist.map { it.toMap() }, UPDATED_AT to at.toTimestamp()))
            WatchlistUpdate.Updated(profile.copy(watchlist = watchlist, updatedAt = at))
        }

    override suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate =
        changeWatchlist(id, at) { watchlist -> watchlist.filterNot { it.symbol == symbol } }

    override suspend fun changeIndicatorSettings(
        id: ProfileId,
        at: Instant,
        change: (List<IndicatorSetting>) -> List<IndicatorSetting>,
    ): Profile? = firestore.transaction { tx ->
        val ref = profiles.document(id.toString())
        val profile = tx.get(ref).get().toProfileOrNull() ?: return@transaction null
        val settings = change(profile.indicatorSettings)
        if (settings == profile.indicatorSettings) return@transaction profile
        tx.update(ref, mapOf(INDICATOR_SETTINGS to settings.map(IndicatorSettingCodec::encode), UPDATED_AT to at.toTimestamp()))
        profile.copy(indicatorSettings = settings, updatedAt = at)
    }

    private suspend fun changeWatchlist(
        id: ProfileId,
        at: Instant,
        change: (List<WatchlistEntry>) -> List<WatchlistEntry>?,
    ): WatchlistUpdate = firestore.transaction { tx ->
        val ref = profiles.document(id.toString())
        val profile = tx.get(ref).get().toProfileOrNull() ?: return@transaction WatchlistUpdate.ProfileNotFound
        val watchlist = change(profile.watchlist) ?: return@transaction WatchlistUpdate.WatchlistFull
        tx.update(ref, mapOf(WATCHLIST to watchlist.map { it.toMap() }, UPDATED_AT to at.toTimestamp()))
        WatchlistUpdate.Updated(profile.copy(watchlist = watchlist, updatedAt = at))
    }

    private fun Profile.toDocument(): FirestoreMap = mapOf(
        NAME to name.value,
        WATCHLIST to watchlist.map { it.toMap() },
        CREATED_AT to createdAt.toTimestamp(),
        UPDATED_AT to updatedAt.toTimestamp(),
        INDICATOR_SETTINGS to indicatorSettings.map(IndicatorSettingCodec::encode),
    )

    private fun WatchlistEntry.toMap(): FirestoreMap = mapOf(
        SYMBOL to symbol.value,
        NAME to name,
        SECTOR to sector,
        TAGS to tags,
    )

    private fun DocumentSnapshot.toProfileOrNull(): Profile? {
        if (!exists()) return null
        val watchlist = (get(WATCHLIST) as? List<*>).orEmpty()
        return Profile(
            id = ProfileId.of(id),
            name = ProfileName.of(requiredString(NAME)),
            watchlist = watchlist.mapNotNull(::watchlistEntryOrNull),
            createdAt = requiredInstant(CREATED_AT),
            updatedAt = requiredInstant(UPDATED_AT),
            indicatorSettings = (get(INDICATOR_SETTINGS) as? List<*>).orEmpty().mapNotNull(IndicatorSettingCodec::decodeOrNull),
        )
    }

    private fun watchlistEntryOrNull(stored: Any?): WatchlistEntry? {
        val map = stored as? Map<*, *> ?: return null
        val symbol = (map[SYMBOL] as? String)?.let(Symbol::parseOrNull) ?: return null
        return WatchlistEntry(
            symbol = symbol,
            name = map[NAME] as? String ?: symbol.ticker,
            sector = map[SECTOR] as? String,
            tags = (map[TAGS] as? List<*>).orEmpty().filterIsInstance<String>(),
        )
    }

    companion object {
        private const val PROFILE_ID = "profileId"
        private const val NAME = "name"
        private const val WATCHLIST = "watchlist"
        private const val SYMBOL = "symbol"
        private const val SECTOR = "sector"
        private const val TAGS = "tags"
        private const val CREATED_AT = "createdAt"
        private const val UPDATED_AT = "updatedAt"
        private const val INDICATOR_SETTINGS = "indicatorSettings"

        fun nameDocumentId(name: ProfileName): String =
            MessageDigest.getInstance("SHA-256")
                .digest(name.normalized.toByteArray(Charsets.UTF_8))
                .toHexString()
    }
}
