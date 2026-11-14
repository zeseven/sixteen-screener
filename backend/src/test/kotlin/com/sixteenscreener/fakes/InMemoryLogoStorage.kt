package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.LogoStorage
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryLogoStorage : LogoStorage {
    private val logos = ConcurrentHashMap<Symbol, ByteArray>()

    override suspend fun find(symbol: Symbol): ByteArray? = logos[symbol]?.copyOf()

    override suspend fun save(symbol: Symbol, svg: ByteArray) {
        logos[symbol] = svg.copyOf()
    }

    fun contains(symbol: Symbol): Boolean = logos.containsKey(symbol)
}

class InMemoryLogoMissRepository : LogoMissRepository {
    private val misses = ConcurrentHashMap<Symbol, Instant>()

    override suspend fun lastMiss(symbol: Symbol): Instant? = misses[symbol]

    override suspend fun recordMiss(symbol: Symbol, checkedAt: Instant) {
        misses[symbol] = checkedAt
    }
}
