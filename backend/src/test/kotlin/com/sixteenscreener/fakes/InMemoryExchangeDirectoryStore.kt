package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.ExchangeDirectory
import com.sixteenscreener.port.outbound.ExchangeDirectoryStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemoryExchangeDirectoryStore : ExchangeDirectoryStore {
    private val directories = ConcurrentHashMap<Pair<SourceId, ExchangeCode>, ExchangeDirectory>()
    private val loadCount = AtomicInteger()
    private val saveCount = AtomicInteger()

    var failLoads: Boolean = false
    var failSaves: Boolean = false

    val loads: Int get() = loadCount.get()
    val saves: Int get() = saveCount.get()

    override suspend fun load(source: SourceId, exchangeCode: ExchangeCode): ExchangeDirectory? {
        loadCount.incrementAndGet()
        check(!failLoads) { "Simulated directory load failure" }
        return directories[source to exchangeCode]
    }

    override suspend fun save(directory: ExchangeDirectory) {
        saveCount.incrementAndGet()
        check(!failSaves) { "Simulated directory save failure" }
        directories[directory.source to directory.exchangeCode] = directory
    }

    fun stored(source: SourceId, exchangeCode: ExchangeCode): ExchangeDirectory? = directories[source to exchangeCode]
}
