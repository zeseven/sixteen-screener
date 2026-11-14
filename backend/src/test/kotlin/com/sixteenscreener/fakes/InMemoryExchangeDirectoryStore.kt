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

    val loads: Int get() = loadCount.get()

    override suspend fun load(source: SourceId, exchangeCode: ExchangeCode): ExchangeDirectory? {
        loadCount.incrementAndGet()
        return directories[source to exchangeCode]
    }

    override suspend fun save(directory: ExchangeDirectory) {
        directories[directory.source to directory.exchangeCode] = directory
    }
}
