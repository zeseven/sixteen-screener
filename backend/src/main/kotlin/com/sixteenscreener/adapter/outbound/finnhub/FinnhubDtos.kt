package com.sixteenscreener.adapter.outbound.finnhub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class SearchResponseDto(
    val result: List<SearchResultDto> = emptyList(),
)

@Serializable
internal data class SearchResultDto(
    val description: String? = null,
    val displaySymbol: String? = null,
    val symbol: String? = null,
    val type: String? = null,
)

@Serializable
internal data class QuoteDto(
    val c: Double? = null,
    val t: Long? = null,
)

@Serializable
internal data class BasicFinancialsDto(
    val metric: JsonObject? = null,
)

@Serializable
internal data class CompanyProfile2Dto(
    val country: String? = null,
    val currency: String? = null,
    val name: String? = null,
    val ticker: String? = null,
    val ipo: String? = null,
    val marketCapitalization: Double? = null,
    val shareOutstanding: Double? = null,
    val weburl: String? = null,
    val finnhubIndustry: String? = null,
    val logo: String? = null,
)

@Serializable
internal data class FinancialsReportedDto(
    val data: List<ReportDto> = emptyList(),
)

@Serializable
internal data class ReportDto(
    val year: Int? = null,
    val quarter: Int? = null,
    val form: String? = null,
    val endDate: String? = null,
    val filedDate: String? = null,
    val report: ReportDataDto? = null,
)

@Serializable
internal data class ReportDataDto(
    val bs: JsonElement? = null,
)

@Serializable
internal data class StockSymbolDto(
    val description: String? = null,
    val displaySymbol: String? = null,
    val symbol: String? = null,
    val type: String? = null,
    val mic: String? = null,
    val currency: String? = null,
)
