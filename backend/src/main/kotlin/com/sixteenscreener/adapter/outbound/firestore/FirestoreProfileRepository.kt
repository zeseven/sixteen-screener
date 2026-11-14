package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
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

    override suspend fun addToWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate =
        firestore.transaction { tx ->
            val ref = profiles.document(id.toString())
            val profile = tx.get(ref).get().toProfileOrNull() ?: return@transaction WatchlistUpdate.ProfileNotFound
            when {
                symbol in profile.watchlist -> {
                    tx.update(ref, UPDATED_AT, at.toTimestamp())
                    WatchlistUpdate.Updated(profile.copy(updatedAt = at))
                }
                profile.watchlist.size >= Profile.MAX_WATCHLIST_SIZE -> WatchlistUpdate.WatchlistFull
                else -> {
                    tx.update(ref, mapOf(WATCHLIST to FieldValue.arrayUnion(symbol.value), UPDATED_AT to at.toTimestamp()))
                    WatchlistUpdate.Updated(profile.copy(watchlist = profile.watchlist + symbol, updatedAt = at))
                }
            }
        }

    override suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol, at: Instant): WatchlistUpdate =
        firestore.transaction { tx ->
            val ref = profiles.document(id.toString())
            val profile = tx.get(ref).get().toProfileOrNull() ?: return@transaction WatchlistUpdate.ProfileNotFound
            tx.update(ref, mapOf(WATCHLIST to FieldValue.arrayRemove(symbol.value), UPDATED_AT to at.toTimestamp()))
            WatchlistUpdate.Updated(profile.copy(watchlist = profile.watchlist - symbol, updatedAt = at))
        }

    private fun Profile.toDocument(): FirestoreMap = mapOf(
        NAME to name.value,
        DEFAULT_SOURCE to defaultSource?.value,
        WATCHLIST to watchlist.map { it.value },
        CREATED_AT to createdAt.toTimestamp(),
        UPDATED_AT to updatedAt.toTimestamp(),
    )

    private fun DocumentSnapshot.toProfileOrNull(): Profile? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        val watchlist = (get(WATCHLIST) as List<String>?).orEmpty()
        return Profile(
            id = ProfileId.of(id),
            name = ProfileName.of(requiredString(NAME)),
            defaultSource = getString(DEFAULT_SOURCE)?.let(SourceId::parseOrNull),
            watchlist = watchlist.mapNotNull(Symbol::parseOrNull),
            createdAt = requiredInstant(CREATED_AT),
            updatedAt = requiredInstant(UPDATED_AT),
        )
    }

    companion object {
        private const val PROFILE_ID = "profileId"
        private const val NAME = "name"
        private const val DEFAULT_SOURCE = "defaultSource"
        private const val WATCHLIST = "watchlist"
        private const val CREATED_AT = "createdAt"
        private const val UPDATED_AT = "updatedAt"

        fun nameDocumentId(name: ProfileName): String =
            MessageDigest.getInstance("SHA-256")
                .digest(name.normalized.toByteArray(Charsets.UTF_8))
                .toHexString()
    }
}
