package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SearchQuery
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

class SearchResponseCache(
    private val clock: Clock,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttl: Duration = DEFAULT_TTL,
) {
    private data class Key(val source: SourceId, val query: String)

    private class Entry(val hits: List<SearchHit>, val storedAt: Instant)

    private val mutex = Mutex()
    private val entries = object : LinkedHashMap<Key, Entry>(maxEntries, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean = size > maxEntries
    }

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    suspend fun get(source: SourceId, query: SearchQuery): List<SearchHit>? = mutex.withLock {
        val key = Key(source, query.normalized)
        val entry = entries[key] ?: return@withLock null
        if (isExpired(entry)) {
            entries.remove(key)
            null
        } else {
            entry.hits
        }
    }

    suspend fun put(source: SourceId, query: SearchQuery, hits: List<SearchHit>) = mutex.withLock {
        entries[Key(source, query.normalized)] = Entry(hits, clock.now())
    }

    private fun isExpired(entry: Entry): Boolean = Duration.between(entry.storedAt, clock.now()) >= ttl

    companion object {
        const val DEFAULT_MAX_ENTRIES = 200
        val DEFAULT_TTL: Duration = Duration.ofMinutes(5)
        private const val LOAD_FACTOR = 0.75f
    }
}
