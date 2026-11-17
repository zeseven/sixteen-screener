package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.BalanceSheetItem
import com.sixteenscreener.app.domain.model.BalanceSheetPeriod
import com.sixteenscreener.app.domain.model.CompanyProfile
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.FiscalPeriod
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Ratio
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class SourceDataMetaDto(
    val source: String,
    val status: String,
    val fetchedAt: String? = null,
)

@Serializable
data class RatioDto(
    val code: String,
    val name: String,
    val unit: String? = null,
    val value: Double? = null,
)

@Serializable
data class PriceAndRatiosResponseDto(
    val meta: SourceDataMetaDto,
    val quote: QuoteDto? = null,
    val ratios: List<RatioDto>,
    val indicators: List<IndicatorDto> = emptyList(),
)

@Serializable
data class BalanceSheetItemDto(
    val concept: String,
    val label: String,
    val value: Double,
    val unit: String? = null,
)

@Serializable
data class BalanceSheetPeriodDto(
    val periodEnd: String,
    val fiscalYear: Int,
    val fiscalPeriod: String,
    val filedAt: String? = null,
    val form: String? = null,
    val items: List<BalanceSheetItemDto>,
)

@Serializable
data class BalanceSheetResponseDto(
    val meta: SourceDataMetaDto,
    val periods: List<BalanceSheetPeriodDto>,
)

@Serializable
data class CompanyProfileDto(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String? = null,
    val country: String? = null,
    val currency: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val ipoDate: String? = null,
    val marketCapitalization: Double? = null,
    val sharesOutstanding: Double? = null,
    val employees: Long? = null,
    val website: String? = null,
    val description: String? = null,
)

@Serializable
data class CompanyProfileResponseDto(
    val meta: SourceDataMetaDto,
    val profile: CompanyProfileDto? = null,
)

fun SourceDataMetaDto.toDomain() = SourceDataMeta(
    source = SourceId(source),
    status = status.toEnumOr(DataStatus.UNKNOWN),
    fetchedAt = fetchedAt?.let(Instant::parse),
)

fun PriceAndRatiosResponseDto.toDomain() = PriceAndRatios(
    meta = meta.toDomain(),
    quote = quote?.toDomain(),
    ratios = ratios.map { Ratio(code = it.code, name = it.name, unit = it.unit.toRatioUnit(), value = it.value) },
    indicators = indicators.map { it.toDomain() },
)

fun BalanceSheetResponseDto.toDomain() = BalanceSheet(
    meta = meta.toDomain(),
    periods = periods.map { period ->
        BalanceSheetPeriod(
            periodEnd = LocalDate.parse(period.periodEnd),
            fiscalYear = period.fiscalYear,
            fiscalPeriod = period.fiscalPeriod.toEnumOr(FiscalPeriod.UNKNOWN),
            filedAt = period.filedAt?.let(LocalDate::parse),
            form = period.form,
            items = period.items.map { BalanceSheetItem(concept = it.concept, label = it.label, value = it.value, unit = it.unit) },
        )
    },
)

fun CompanyProfileDto.toDomain() = CompanyProfile(
    symbol = Symbol(symbol),
    ticker = ticker,
    exchange = exchange,
    name = name,
    market = market.toMarket(),
    country = country,
    currency = currency,
    sector = sector,
    industry = industry,
    ipoDate = ipoDate?.let(LocalDate::parse),
    marketCapitalization = marketCapitalization,
    sharesOutstanding = sharesOutstanding,
    employees = employees,
    website = website,
    description = description,
)

fun CompanyProfileResponseDto.toDomain() = CompanyProfileData(meta = meta.toDomain(), profile = profile?.toDomain())
