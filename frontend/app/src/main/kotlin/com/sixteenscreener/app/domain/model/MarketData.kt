package com.sixteenscreener.app.domain.model

/** Level of a ratio indicator. */
enum class IndicatorLevel { BAD, WARNING, GOOD, UNKNOWN }

/**
 * Ratio whose value falls into a displayed interval.
 */
data class Indicator(
    /** Ratio code shared across sources, e.g. `PE`. */
    val ratioCode: String,
    /** Display name, e.g. `P/E`. */
    val name: String,
    /** Interval the value falls into. */
    val level: IndicatorLevel,
)

/**
 * Quote and indicators of one watchlist row as returned by `GET /watchlist/market-data`.
 */
data class MarketData(
    /** Canonical company identifier. */
    val symbol: Symbol,
    /** Freshness of the quote, or why it is missing or stale. */
    val status: DataStatus,
    /** Quote, `null` when none is available. */
    val quote: Quote?,
    /** Indicators of the company; empty until indicators are implemented. */
    val indicators: List<Indicator>,
)

/** Outcome of the last market data load of a watchlist row. */
sealed interface MarketDataLoad {
    /** A request for the row is running. */
    data object Loading : MarketDataLoad

    /** The backend returned the row with [status]. */
    data class Loaded(val status: DataStatus) : MarketDataLoad

    /** The request failed: network error or error response of the backend. */
    data object Failed : MarketDataLoad
}

/**
 * Market data the app has for a watchlist row: the last received values and the outcome of the last load (ADR-058).
 */
data class RowMarketData(
    /** Last received quote, `null` when none has been received. */
    val quote: Quote? = null,
    /** Last received indicators. */
    val indicators: List<Indicator> = emptyList(),
    val lastLoad: MarketDataLoad = MarketDataLoad.Loading,
) {
    /** Whether the last load failed: a failed request, or a source limit or source error reported by the backend. */
    val isLastLoadFailed: Boolean
        get() = when (lastLoad) {
            MarketDataLoad.Failed -> true
            is MarketDataLoad.Loaded -> lastLoad.status in FAILED_STATUSES
            MarketDataLoad.Loading -> false
        }

    private companion object {
        val FAILED_STATUSES = setOf(DataStatus.LIMIT_REACHED, DataStatus.SOURCE_ERROR)
    }
}

/** This row's data while a new load runs; the previous values stay. */
fun RowMarketData?.loading(): RowMarketData = (this ?: RowMarketData()).copy(lastLoad = MarketDataLoad.Loading)

/** This row's data after a failed request; the previous values stay. */
fun RowMarketData?.failed(): RowMarketData = (this ?: RowMarketData()).copy(lastLoad = MarketDataLoad.Failed)

/**
 * This row's data after [data] was received. A source limit or source error keeps the previous values unless the backend
 * returned cached ones; any other status replaces them.
 */
fun RowMarketData?.loadedWith(data: MarketData): RowMarketData {
    val loaded = RowMarketData(data.quote, data.indicators, MarketDataLoad.Loaded(data.status))
    if (!loaded.isLastLoadFailed) return loaded
    val previous = this ?: RowMarketData()
    return loaded.copy(
        quote = data.quote ?: previous.quote,
        indicators = data.indicators.ifEmpty { previous.indicators },
    )
}
