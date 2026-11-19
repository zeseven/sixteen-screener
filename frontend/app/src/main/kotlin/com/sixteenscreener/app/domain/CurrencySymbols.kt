package com.sixteenscreener.app.domain

/**
 * Single-character symbols of ISO 4217 currency codes; hardcoded because the locale-dependent JDK symbols give
 * multi-character forms like `CA$` and `US$`.
 */
object CurrencySymbols {
    private const val DOLLAR = "$"

    private val SYMBOLS: Map<String, String> =
        listOf("USD", "CAD", "AUD", "NZD", "HKD", "SGD", "TWD", "MXN", "ARS", "CLP", "COP").associateWith { DOLLAR } +
            mapOf(
                "EUR" to "€",
                "GBP" to "£",
                "JPY" to "¥",
                "CNY" to "¥",
                "INR" to "₹",
                "KRW" to "₩",
                "ILS" to "₪",
                "TRY" to "₺",
                "RUB" to "₽",
                "THB" to "฿",
                "VND" to "₫",
                "PHP" to "₱",
                "NGN" to "₦",
                "ZAR" to "R",
            )

    /** Symbol of [currency], matched case-insensitively after trimming; `null` for a code without a single-character symbol. */
    fun of(currency: String): String? = SYMBOLS[currency.trim().uppercase()]
}
