package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.IndicatorDefault
import com.sixteenscreener.port.outbound.IndicatorDefaultsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryIndicatorDefaultsRepository(var stored: List<IndicatorDefault>? = null) : IndicatorDefaultsRepository {
    private val mutex = Mutex()

    var reads: Int = 0
        private set

    override suspend fun getOrCreate(initial: List<IndicatorDefault>): List<IndicatorDefault> = mutex.withLock {
        reads++
        stored ?: initial.also { stored = it }
    }
}
