package com.sixteenscreener

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry

fun watchlistEntry(ticker: String, sector: String? = null, tags: List<String> = emptyList(), name: String = Symbol.of(ticker).ticker) =
    WatchlistEntry(Symbol.of(ticker), name, sector, tags)

fun watchlistEntries(tickers: List<String>): List<WatchlistEntry> = tickers.map { watchlistEntry(it) }

fun watchlistEntries(vararg tickers: String): List<WatchlistEntry> = watchlistEntries(tickers.toList())
