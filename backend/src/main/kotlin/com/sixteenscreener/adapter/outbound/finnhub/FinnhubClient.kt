package com.sixteenscreener.adapter.outbound.finnhub

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.BalanceSheetSource
import com.sixteenscreener.port.outbound.MarketDataSource
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Finnhub REST API client (`https://finnhub.io/api/v1`). Symbols are sent as they are: Finnhub uses the same
 * `TICKER.EXCHANGE` suffix form as [Symbol] (`AAPL`, `SAP.DE`).
 */
class FinnhubClient(
    override val id: SourceId,
    baseUrl: String,
    private val apiKey: String,
    private val call: SourceHttpCall,
    private val directoryTimeout: Duration = DEFAULT_DIRECTORY_TIMEOUT,
) : MarketDataSource, BalanceSheetSource {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun search(query: String): SourceResult<List<SearchHit>> =
        get("/search", "q" to query) { response -> FinnhubMapper.searchHits(response.body()) }

    override suspend fun quote(listing: ListedSymbol): SourceResult<Quote> =
        get("/quote", "symbol" to listing.symbol.value) { response ->
            FinnhubMapper.quote(response.body(), listing.currency)
        }

    override suspend fun ratios(symbol: Symbol): SourceResult<Ratios> =
        get("/stock/metric", "symbol" to symbol.value, "metric" to "all") { response ->
            FinnhubMapper.ratios(response.body(), symbol)
        }

    override suspend fun dividends(symbol: Symbol): SourceResult<Dividends> = SourceResult.NotSupported(outcome = null)

    override suspend fun companyProfile(symbol: Symbol): SourceResult<CompanyProfile> =
        get("/stock/profile2", "symbol" to symbol.value) { response -> FinnhubMapper.companyProfile(response.body()) }

    override suspend fun balanceSheet(symbol: Symbol): SourceResult<BalanceSheet> =
        get("/stock/financials-reported", "symbol" to symbol.value, "freq" to "annual") { response ->
            FinnhubMapper.balanceSheet(response.body())
        }

    override suspend fun exchangeSymbols(exchangeCode: ExchangeCode): SourceResult<List<ListedSymbol>> =
        get("/stock/symbol", "exchange" to exchangeCode.value, timeout = directoryTimeout) { response ->
            decodeListedSymbols(response).takeIf { it.isNotEmpty() }
        }

    private suspend fun <T> get(
        path: String,
        vararg parameters: Pair<String, String>,
        timeout: Duration? = null,
        mapResponse: suspend (HttpResponse) -> T?,
    ): SourceResult<T> {
        val request: HttpRequestBuilder.() -> Unit = {
            url("$baseUrl$path")
            parameters.forEach { (name, value) -> parameter(name, value) }
            header(TOKEN_HEADER, apiKey)
        }
        return call.execute(request, errorStatus, requestTimeout = timeout, mapResponse = mapResponse)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun decodeListedSymbols(response: HttpResponse): List<ListedSymbol> {
        val channel = response.bodyAsChannel()
        return withContext(Dispatchers.IO) {
            channel.toInputStream().use { input ->
                SourceHttpClient.json
                    .decodeToSequence(input, StockSymbolDto.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
                    .mapNotNull(FinnhubMapper::listedSymbol)
                    .toList()
            }
        }
    }

    companion object {
        const val TOKEN_HEADER = "X-Finnhub-Token"

        val DEFAULT_DIRECTORY_TIMEOUT: Duration = 60.seconds

        private const val FORBIDDEN = 403

        private val errorStatus = ErrorStatusMapping.overriding(
            FORBIDDEN to { outcome -> SourceResult.NotSupported(outcome) },
        )
    }
}
