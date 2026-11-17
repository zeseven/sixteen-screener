package com.sixteenscreener.app.testing

import com.sixteenscreener.app.data.local.WatchlistStore
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.WatchlistItem

class FakeWatchlistStore(vararg initial: Pair<ProfileId, List<WatchlistItem>>) : WatchlistStore {

    val stored = mutableMapOf(*initial)

    val writes = mutableListOf<String>()

    var read: suspend (ProfileId) -> List<WatchlistItem>? = { stored[it] }

    override suspend fun read(profileId: ProfileId): List<WatchlistItem>? = read.invoke(profileId)

    override suspend fun save(profileId: ProfileId, items: List<WatchlistItem>): Boolean {
        writes += "save(${profileId.value}, ${items.joinToString(",") { it.symbol.value }})"
        stored[profileId] = items
        return true
    }

    override suspend fun clear(profileId: ProfileId): Boolean {
        writes += "clear(${profileId.value})"
        stored -= profileId
        return true
    }
}
