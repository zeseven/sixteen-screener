package com.sixteenscreener.app.domain.model

/** Level of a ratio indicator or of an indicator interval; `NEUTRAL` values make no indicator. */
enum class IndicatorLevel { GOOD, WARNING, NEUTRAL, BAD, UNKNOWN }

/**
 * Switched on ratio whose value falls into a `GOOD`, `WARNING` or `BAD` interval.
 */
data class Indicator(
    /** Ratio code shared across sources, e.g. `PE`. */
    val ratioCode: String,
    /** Short name shown on the company row, e.g. `P/E`. */
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
    /** Indicators of the company in display order. */
    val indicators: List<Indicator>,
    /** Freshness of the ratios the indicators are calculated from, or why they are missing or stale. */
    val indicatorsStatus: DataStatus = status,
)

/** Outcome of the last market data load of a watchlist row. */
sealed interface MarketDataLoad {
    /** A request for the row is running. */
    data object Loading : MarketDataLoad

    /** The backend returned the row with the quote [status] and the ratio [indicatorsStatus]. */
    data class Loaded(val status: DataStatus, val indicatorsStatus: DataStatus = status) : MarketDataLoad

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
    /** Whether the last load of the quote failed: a failed request, or a source limit or source error reported by the backend. */
    val isLastLoadFailed: Boolean
        get() = isFailed { it.status }

    /** Whether the last load of the ratios failed: a failed request, or a source limit or source error of the ratios. */
    val isIndicatorsLoadFailed: Boolean
        get() = isFailed { it.indicatorsStatus }

    private inline fun isFailed(status: (MarketDataLoad.Loaded) -> DataStatus): Boolean = when (lastLoad) {
        MarketDataLoad.Failed -> true
        is MarketDataLoad.Loaded -> status(lastLoad) in FAILED_STATUSES
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
 * returned cached ones, and so does a source that was not called (`NOT_REQUESTED`), drawn in the default text color; any other
 * status replaces them. The quote follows its status and the indicators the ratio status.
 */
fun RowMarketData?.loadedWith(data: MarketData): RowMarketData {
    val loaded = RowMarketData(data.quote, data.indicators, MarketDataLoad.Loaded(data.status, data.indicatorsStatus))
    val previous = this ?: RowMarketData()
    val keepsQuote = loaded.isLastLoadFailed || data.status == DataStatus.NOT_REQUESTED
    val keepsIndicators = loaded.isIndicatorsLoadFailed || data.indicatorsStatus == DataStatus.NOT_REQUESTED
    return loaded.copy(
        quote = if (keepsQuote) data.quote ?: previous.quote else data.quote,
        indicators = if (keepsIndicators) data.indicators.ifEmpty { previous.indicators } else data.indicators,
    )
}
