package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CompanyProfileData
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.PriceAndRatios
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SourceDataMetaDto(
    val source: String,
    val status: String,
    val fetchedAt: String?,
)

@Serializable
data class RatioDto(
    val code: String,
    val name: String,
    val unit: String,
    val value: Double?,
)

@Serializable
data class PriceAndRatiosResponseDto(
    val meta: SourceDataMetaDto,
    val quote: QuoteDto?,
    val ratios: List<RatioDto>,
    val indicators: List<IndicatorDto>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BalanceSheetItemDto(
    val concept: String,
    val label: String,
    val value: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
)

@Serializable
data class BalanceSheetPeriodDto(
    val periodEnd: String,
    val fiscalYear: Int,
    val fiscalPeriod: String,
    val filedAt: String?,
    val form: String?,
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
    val market: String?,
    val country: String?,
    val currency: String?,
    val sector: String?,
    val industry: String?,
    val ipoDate: String?,
    val marketCapitalization: Double?,
    val sharesOutstanding: Double?,
    val employees: Int?,
    val website: String?,
    val description: String?,
)

@Serializable
data class CompanyProfileResponseDto(
    val meta: SourceDataMetaDto,
    val profile: CompanyProfileDto?,
)

private fun metaOf(source: SourceId, status: DataStatus, fetchedAt: Instant?) =
    SourceDataMetaDto(source = source.value, status = status.name, fetchedAt = fetchedAt?.toString())

private fun SourceData<*>.toMetaDto() = metaOf(source, status, fetchedAt)

private fun Ratio.toDto() = RatioDto(code = code.name, name = name, unit = unit.name, value = value)

private fun BalanceSheetItem.toDto() = BalanceSheetItemDto(concept = concept, label = label, value = value, unit = unit)

private fun BalanceSheetPeriod.toDto() = BalanceSheetPeriodDto(
    periodEnd = periodEnd.toString(),
    fiscalYear = fiscalYear,
    fiscalPeriod = fiscalPeriod.name,
    filedAt = filedAt?.toString(),
    form = form,
    items = items.map { it.toDto() },
)

fun PriceAndRatios.toDto() = PriceAndRatiosResponseDto(
    meta = metaOf(source, status, fetchedAt),
    quote = quote.toQuoteDto(),
    ratios = ratios.data?.items.orEmpty().map { it.toDto() },
    indicators = indicators.map { it.toDto() },
)

fun SourceData<BalanceSheet>.toDto() = BalanceSheetResponseDto(
    meta = toMetaDto(),
    periods = data?.periods.orEmpty().map { it.toDto() },
)

fun CompanyProfileData.toDto() = CompanyProfileResponseDto(
    meta = profile.toMetaDto(),
    profile = profile.data?.let { data ->
        CompanyProfileDto(
            symbol = identity.symbol.value,
            ticker = identity.ticker,
            exchange = identity.exchange,
            name = identity.name,
            market = identity.market?.name,
            country = data.country,
            currency = data.currency,
            sector = data.sector,
            industry = data.industry,
            ipoDate = data.ipoDate?.toString(),
            marketCapitalization = data.marketCapitalization,
            sharesOutstanding = data.sharesOutstanding,
            employees = data.employees,
            website = data.website,
            description = data.description,
        )
    },
)
