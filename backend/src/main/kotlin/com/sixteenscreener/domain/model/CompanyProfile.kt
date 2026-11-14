package com.sixteenscreener.domain.model

import java.time.LocalDate

/**
 * Company profile data as provided by a source. Symbol, ticker, exchange and market are resolved by the backend
 * from the symbol and the exchange directory, not taken from the source.
 *
 * @property name company name
 * @property country ISO 3166-1 alpha-2 country code
 * @property currency ISO 4217 currency code
 * @property sector sector as provided by the source
 * @property industry industry as provided by the source
 * @property ipoDate IPO date
 * @property marketCapitalization market capitalization in [currency]
 * @property sharesOutstanding number of shares outstanding
 * @property employees number of employees
 * @property website company website
 * @property description company description
 */
data class CompanyProfile(
    val name: String?,
    val country: String?,
    val currency: String?,
    val sector: String?,
    val industry: String?,
    val ipoDate: LocalDate?,
    val marketCapitalization: Double?,
    val sharesOutstanding: Double?,
    val employees: Int?,
    val website: String?,
    val description: String?,
)
