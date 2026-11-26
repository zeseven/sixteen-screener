package com.sixteenscreener.adapter.outbound.yahoo

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.BalanceSheetSource
import com.sixteenscreener.port.outbound.MarketDataSource
import io.ktor.client.call.body
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodeURLPathPart

/**
 * Client of the Yahoo proxy (`yahoo-proxy`), which reads Yahoo Finance with `yfinance` (ADR-088). One proxy call per method;
 * symbols are sent as Yahoo symbols (`SAP.DE`, `SHEL.L`, `BRK-B`), and a symbol of an exchange without a Yahoo suffix is not
 * supported without a request. A proxy `404` (Yahoo has no such data) is a successful call without data; a `429` is a source
 * error, because the source has no limit to saturate (ADR-089). Search and symbol lists are not supported.
 */
class YahooClient(
    override val id: SourceId,
    baseUrl: String,
    private val call: SourceHttpCall,
) : MarketDataSource, BalanceSheetSource {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun search(query: String): SourceResult<List<SearchHit>> = NOT_SUPPORTED

    override suspend fun quote(listing: ListedSymbol): SourceResult<Quote> =
        get(listing.symbol, QUOTE) { response -> YahooMapper.quote(response.body(), listing.currency) }

    override suspend fun ratios(symbol: Symbol): SourceResult<Ratios> =
        get(symbol, STATISTICS) { response -> YahooMapper.ratios(response.body()) }

    override suspend fun dividends(symbol: Symbol): SourceResult<Dividends> =
        get(symbol, DIVIDENDS) { response -> YahooMapper.dividends(response.body()) }

    override suspend fun companyProfile(symbol: Symbol): SourceResult<CompanyProfile> =
        get(symbol, PROFILE) { response -> YahooMapper.companyProfile(response.body()) }

    override suspend fun balanceSheet(symbol: Symbol): SourceResult<BalanceSheet> =
        get(symbol, BALANCE_SHEET) { response -> YahooMapper.balanceSheet(response.body()) }

    override suspend fun exchangeSymbols(exchangeCode: ExchangeCode): SourceResult<List<ListedSymbol>> = NOT_SUPPORTED

    private suspend fun <T> get(symbol: Symbol, data: String, mapResponse: suspend (HttpResponse) -> T?): SourceResult<T> {
        val yahooSymbol = yahooSymbolOf(symbol) ?: return NOT_SUPPORTED
        return call.execute(
            request = { url("$baseUrl/symbols/${yahooSymbol.encodeURLPathPart()}/$data") },
            errorStatus = errorStatus,
            mapResponse = mapResponse,
        )
    }

    companion object {
        private const val QUOTE = "quote"
        private const val STATISTICS = "statistics"
        private const val DIVIDENDS = "dividends"
        private const val PROFILE = "profile"
        private const val BALANCE_SHEET = "balance-sheet"

        private const val NOT_FOUND = 404
        private const val TOO_MANY_REQUESTS = 429

        private val NOT_SUPPORTED = SourceResult.NotSupported(outcome = null)

        private val errorStatus = ErrorStatusMapping.overriding(
            NOT_FOUND to { outcome -> SourceResult.NoData(SourceCallOutcome.success(outcome.at)) },
            TOO_MANY_REQUESTS to { outcome -> SourceResult.SourceError(outcome) },
        )

        /**
         * Yahoo symbol of [symbol]: the ticker with the Yahoo suffix of its exchange (`SAP.DE`, `SHEL.L`); US tickers have
         * no suffix and their dots become dashes (`BRK.B` is `BRK-B`). Null for an exchange without a Yahoo suffix.
         */
        fun yahooSymbolOf(symbol: Symbol): String? {
            val suffix = KnownExchangeCodes.yahooSuffixOf(symbol.exchangeCode) ?: return null
            return if (suffix.isEmpty()) symbol.ticker.replace('.', '-') else "${symbol.ticker}.$suffix"
        }
    }
}
