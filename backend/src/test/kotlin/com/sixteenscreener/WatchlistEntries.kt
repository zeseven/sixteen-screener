package com.sixteenscreener

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry

fun watchlistEntry(ticker: String, sector: String? = null, tags: List<String> = emptyList()) =
    WatchlistEntry(Symbol.of(ticker), sector, tags)

fun watchlistEntries(tickers: List<String>): List<WatchlistEntry> = tickers.map(::watchlistEntry)

fun watchlistEntries(vararg tickers: String): List<WatchlistEntry> = watchlistEntries(tickers.toList())
