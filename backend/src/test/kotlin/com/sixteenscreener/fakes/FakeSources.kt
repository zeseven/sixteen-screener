package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.BalanceSheetSource
import com.sixteenscreener.port.outbound.LogoProvider
import com.sixteenscreener.port.outbound.MarketDataSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val NOT_CONFIGURED: SourceResult<Nothing> = SourceResult.NotSupported(outcome = null)

class FakeMarketDataSource(override val id: SourceId = SourceId.of("finnhub")) : MarketDataSource {
    val searchResults = ConcurrentHashMap<String, SourceResult<List<SearchHit>>>()
    val quotes = ConcurrentHashMap<Symbol, SourceResult<Quote>>()
    val ratios = ConcurrentHashMap<Symbol, SourceResult<Ratios>>()
    val profiles = ConcurrentHashMap<Symbol, SourceResult<CompanyProfile>>()
    val exchanges = ConcurrentHashMap<ExchangeCode, SourceResult<List<ListedSymbol>>>()
    val calls: MutableList<String> = CopyOnWriteArrayList()

    override suspend fun search(query: String): SourceResult<List<SearchHit>> =
        recorded("search:$query") { searchResults[query] }

    override suspend fun quote(listing: ListedSymbol): SourceResult<Quote> =
        recorded("quote:${listing.symbol}") { quotes[listing.symbol] }

    override suspend fun ratios(symbol: Symbol): SourceResult<Ratios> =
        recorded("ratios:$symbol") { ratios[symbol] }

    override suspend fun companyProfile(symbol: Symbol): SourceResult<CompanyProfile> =
        recorded("profile:$symbol") { profiles[symbol] }

    override suspend fun exchangeSymbols(exchangeCode: ExchangeCode): SourceResult<List<ListedSymbol>> =
        recorded("exchange:$exchangeCode") { exchanges[exchangeCode] }

    private fun <T> recorded(call: String, result: () -> SourceResult<T>?): SourceResult<T> {
        calls += call
        return result() ?: NOT_CONFIGURED
    }
}

class FakeBalanceSheetSource(override val id: SourceId) : BalanceSheetSource {
    val balanceSheets = ConcurrentHashMap<Symbol, SourceResult<BalanceSheet>>()
    val calls: MutableList<Symbol> = CopyOnWriteArrayList()

    override suspend fun balanceSheet(symbol: Symbol): SourceResult<BalanceSheet> {
        calls += symbol
        return balanceSheets[symbol] ?: NOT_CONFIGURED
    }
}

class FakeLogoProvider(override val id: SourceId = SourceId.of("logoapi")) : LogoProvider {
    val logos = ConcurrentHashMap<Symbol, SourceResult<ByteArray>>()
    val calls: MutableList<Symbol> = CopyOnWriteArrayList()

    override suspend fun logo(symbol: Symbol): SourceResult<ByteArray> {
        calls += symbol
        return logos[symbol] ?: NOT_CONFIGURED
    }
}
