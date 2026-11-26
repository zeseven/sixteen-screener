package com.sixteenscreener.adapter.outbound.yahoo

import kotlinx.serialization.Serializable

@Serializable
internal data class QuoteDto(
    val symbol: String? = null,
    val price: Double? = null,
    val currency: String? = null,
)

@Serializable
internal data class StatisticsDto(
    val symbol: String? = null,
    val currency: String? = null,
    val ratios: Map<String, Double> = emptyMap(),
    val amounts: Map<String, Double> = emptyMap(),
)

@Serializable
internal data class ProfileDto(
    val symbol: String? = null,
    val name: String? = null,
    val quoteType: String? = null,
    val typeDisplay: String? = null,
    val currency: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val country: String? = null,
    val website: String? = null,
    val description: String? = null,
    val employees: Int? = null,
)

@Serializable
internal data class DividendsDto(
    val symbol: String? = null,
    val currency: String? = null,
    val dividends: List<DividendDto> = emptyList(),
)

@Serializable
internal data class DividendDto(
    val exDate: String? = null,
    val amount: Double? = null,
)

@Serializable
internal data class BalanceSheetDto(
    val symbol: String? = null,
    val currency: String? = null,
    val years: List<BalanceSheetYearDto> = emptyList(),
)

@Serializable
internal data class BalanceSheetYearDto(
    val fiscalYearEnd: String? = null,
    val items: Map<String, Double> = emptyMap(),
)
