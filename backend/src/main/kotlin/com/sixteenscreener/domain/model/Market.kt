package com.sixteenscreener.domain.model

/**
 * Market detected by exchange. UK (LSE) and Swiss (SIX) exchanges belong to [EU] (ADR-014).
 * Exchanges outside both markets have no market (ADR-035).
 */
enum class Market {
    /** US exchanges. */
    US,

    /** EU exchanges together with LSE and SIX. */
    EU,
}
