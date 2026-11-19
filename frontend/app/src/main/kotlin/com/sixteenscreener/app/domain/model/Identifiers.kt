package com.sixteenscreener.app.domain.model

import kotlinx.serialization.Serializable

/** Canonical company identifier assigned by the backend, e.g. `AAPL` or `SAP.DE`. */
@Serializable
@JvmInline
value class Symbol(val value: String)

/** Profile ID returned by the backend for a profile name. */
@Serializable
@JvmInline
value class ProfileId(val value: String)

/** Configured data source identifier, e.g. `finnhub` or `sec-edgar`. */
@Serializable
@JvmInline
value class SourceId(val value: String) {
    companion object {
        val FINNHUB = SourceId("finnhub")
        val SEC_EDGAR = SourceId("sec-edgar")
        val EODHD = SourceId("eodhd")
    }
}
