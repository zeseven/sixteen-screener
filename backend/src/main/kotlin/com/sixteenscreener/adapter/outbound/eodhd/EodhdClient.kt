package com.sixteenscreener.adapter.outbound.eodhd

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.MarketDataSource
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodeURLPathPart
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * EODHD REST API client (`https://eodhd.com/api`), free plan: delayed quotes and dividends of the last year (ADR-083).
 * Symbols are sent as `{TICKER}.{EODHD exchange code}` (`SAP.DE` is `SAP.XETRA`); a symbol of an exchange without an
 * EODHD code is not supported without a request. Search, ratios, company profiles and symbol lists are not supported.
 */
class EodhdClient(
    override val id: SourceId,
    baseUrl: String,
    private val apiToken: String,
    private val call: SourceHttpCall,
    private val clock: Clock,
) : MarketDataSource {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun search(query: String): SourceResult<List<SearchHit>> = NOT_SUPPORTED

    override suspend fun quote(listing: ListedSymbol): SourceResult<Quote> {
        val symbol = eodhdSymbolOf(listing.symbol) ?: return NOT_SUPPORTED
        val priceDivisor = priceDivisorOf(listing.symbol)
        return get("/real-time/$symbol") { response -> EodhdMapper.quote(response.body(), listing.currency, priceDivisor) }
    }

    override suspend fun ratios(symbol: Symbol): SourceResult<Ratios> = NOT_SUPPORTED

    override suspend fun dividends(symbol: Symbol): SourceResult<Dividends> {
        val eodhdSymbol = eodhdSymbolOf(symbol) ?: return NOT_SUPPORTED
        val from = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC).minusYears(1)
        return get("/div/$eodhdSymbol", "from" to from.toString()) { response -> EodhdMapper.dividends(response.body()) }
    }

    override suspend fun companyProfile(symbol: Symbol): SourceResult<CompanyProfile> = NOT_SUPPORTED

    override suspend fun exchangeSymbols(exchangeCode: ExchangeCode): SourceResult<List<ListedSymbol>> = NOT_SUPPORTED

    private suspend fun <T> get(
        path: String,
        vararg parameters: Pair<String, String>,
        mapResponse: suspend (HttpResponse) -> T?,
    ): SourceResult<T> {
        val request: HttpRequestBuilder.() -> Unit = {
            url("$baseUrl$path")
            parameter(FORMAT_PARAMETER, JSON_FORMAT)
            parameters.forEach { (name, value) -> parameter(name, value) }
            bearerAuth(apiToken)
        }
        return call.execute(request, errorStatus, mapResponse = mapResponse)
    }

    companion object {
        private const val FORMAT_PARAMETER = "fmt"
        private const val JSON_FORMAT = "json"
        private const val PAYMENT_REQUIRED = 402
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404

        private const val MINOR_UNITS_PER_MAJOR_UNIT = 100.0
        private const val MAJOR_UNIT_DIVISOR = 1.0

        private val NOT_SUPPORTED = SourceResult.NotSupported(outcome = null)

        private val exchangesQuotedInMinorUnits = setOf("LSE", "JSE")

        private val errorStatus = ErrorStatusMapping.overriding(
            PAYMENT_REQUIRED to { outcome -> SourceResult.LimitReached(outcome) },
            FORBIDDEN to { outcome -> SourceResult.NotSupported(outcome) },
            NOT_FOUND to { outcome -> SourceResult.NoData(outcome) },
        )

        /** EODHD symbol of [symbol]: dots of the ticker become dashes (`BRK.B` is `BRK-B.US`); null without an EODHD code. */
        fun eodhdSymbolOf(symbol: Symbol): String? =
            KnownExchangeCodes.eodhdCodeOf(symbol.exchangeCode)
                ?.let { code -> "${symbol.ticker.replace('.', '-')}.$code".encodeURLPathPart() }

        private fun priceDivisorOf(symbol: Symbol): Double =
            if (KnownExchangeCodes.eodhdCodeOf(symbol.exchangeCode) in exchangesQuotedInMinorUnits) {
                MINOR_UNITS_PER_MAJOR_UNIT
            } else {
                MAJOR_UNIT_DIVISOR
            }
    }
}
