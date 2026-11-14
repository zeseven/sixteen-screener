package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.LogoStorage
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryLogoStorage : LogoStorage {
    private val logos = ConcurrentHashMap<Symbol, ByteArray>()

    var failingReads: Boolean = false
    var failingWrites: Boolean = false

    override suspend fun find(symbol: Symbol): ByteArray? {
        check(!failingReads) { "Storage unavailable" }
        return logos[symbol]?.copyOf()
    }

    override suspend fun save(symbol: Symbol, svg: ByteArray) {
        check(!failingWrites) { "Storage unavailable" }
        logos[symbol] = svg.copyOf()
    }

    fun contains(symbol: Symbol): Boolean = logos.containsKey(symbol)
}

class InMemoryLogoMissRepository : LogoMissRepository {
    private val misses = ConcurrentHashMap<Symbol, Instant>()

    var failing: Boolean = false

    override suspend fun lastMiss(symbol: Symbol): Instant? {
        check(!failing) { "Repository unavailable" }
        return misses[symbol]
    }

    override suspend fun recordMiss(symbol: Symbol, checkedAt: Instant) {
        check(!failing) { "Repository unavailable" }
        misses[symbol] = checkedAt
    }
}
