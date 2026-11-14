package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceId
import java.time.Instant

/**
 * Exchange directory downloaded from a source.
 *
 * @property source source the directory was downloaded from
 * @property exchangeCode exchange of the directory
 * @property symbols listed symbols
 * @property downloadedAt when the directory was downloaded from the source
 */
data class ExchangeDirectory(
    val source: SourceId,
    val exchangeCode: ExchangeCode,
    val symbols: List<ListedSymbol>,
    val downloadedAt: Instant,
)

/**
 * Persistent storage of exchange directories (`directory/{source}/{exchangeCode}.json` in the GCS bucket, ADR-034).
 */
interface ExchangeDirectoryStore {
    /** Stored directory of [exchangeCode] from [source], or null when none is stored. */
    suspend fun load(source: SourceId, exchangeCode: ExchangeCode): ExchangeDirectory?

    /** Stores [directory], replacing a previously stored one of the same source and exchange. */
    suspend fun save(directory: ExchangeDirectory)
}
