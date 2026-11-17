package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.LogoStorage
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryLogoStorage(override val format: ImageFormat = ImageFormat.SVG) : LogoStorage {
    private val logos = ConcurrentHashMap<Symbol, ByteArray>()

    var failingReads: Boolean = false
    var failingWrites: Boolean = false

    override suspend fun find(symbol: Symbol): ByteArray? {
        check(!failingReads) { "Storage unavailable" }
        return logos[symbol]?.copyOf()
    }

    override suspend fun save(symbol: Symbol, image: ByteArray) {
        check(!failingWrites) { "Storage unavailable" }
        logos[symbol] = image.copyOf()
    }

    fun contains(symbol: Symbol): Boolean = logos.containsKey(symbol)
}

class InMemoryLogoMissRepository : LogoMissRepository {
    private val misses = ConcurrentHashMap<Pair<SourceId, Symbol>, Instant>()

    var failing: Boolean = false

    override suspend fun lastMiss(source: SourceId, symbol: Symbol): Instant? {
        check(!failing) { "Repository unavailable" }
        return misses[source to symbol]
    }

    override suspend fun recordMiss(source: SourceId, symbol: Symbol, checkedAt: Instant) {
        check(!failing) { "Repository unavailable" }
        misses[source to symbol] = checkedAt
    }
}
