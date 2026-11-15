package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.domain.model.WatchlistItem

internal fun CompanyIdentityResolver.watchlistItemOf(entry: WatchlistEntry, listing: ListedSymbol?): WatchlistItem =
    WatchlistItem(identify(entry.symbol, listing).copy(name = entry.name), entry.sector, entry.tags)
