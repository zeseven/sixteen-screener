package com.sixteenscreener.fakes

import com.sixteenscreener.port.outbound.SecCikRepository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemorySecCikRepository : SecCikRepository {
    private data class Record(val cik: Long, val expireAt: Instant)

    private val records = ConcurrentHashMap<String, Record>()

    var failing: Boolean = false

    override suspend fun find(ticker: String, now: Instant): Long? {
        check(!failing) { "Repository unavailable" }
        return records[ticker]?.takeIf { now < it.expireAt }?.cik
    }

    override suspend fun save(ticker: String, cik: Long, storedAt: Instant) {
        check(!failing) { "Repository unavailable" }
        records[ticker] = Record(cik, storedAt.plus(TTL))
    }

    fun stored(): Map<String, Long> = records.mapValues { it.value.cik }

    private companion object {
        private val TTL: Duration = Duration.ofDays(14)
    }
}
