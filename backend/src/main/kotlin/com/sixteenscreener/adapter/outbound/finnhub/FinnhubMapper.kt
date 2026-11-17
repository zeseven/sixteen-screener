package com.sixteenscreener.adapter.outbound.finnhub

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.RatioUnit
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.Symbol
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate

internal object FinnhubMapper {
    private const val MILLION = 1_000_000.0
    private const val ISO_DATE_LENGTH = 10
    private const val ANNUAL_QUARTER = 0

    val metricKeys: Map<RatioCode, List<String>> = mapOf(
        RatioCode.PE to listOf("peTTM", "peBasicExclExtraTTM", "peExclExtraTTM", "peAnnual"),
        RatioCode.PB to listOf("pb", "pbQuarterly", "pbAnnual"),
        RatioCode.PS to listOf("psTTM", "psAnnual"),
        RatioCode.EV_EBITDA to listOf("evEbitdaTTM", "evEbitdaAnnual"),
        RatioCode.EV_REVENUE to listOf("evRevenueTTM"),
        RatioCode.PCF to listOf("pcfShareTTM", "pcfShareAnnual"),
        RatioCode.PFCF to listOf("pfcfShareTTM", "pfcfShareAnnual"),
        RatioCode.PEG to listOf("pegTTM"),
        RatioCode.ROE to listOf("roeTTM", "roeRfy"),
        RatioCode.ROA to listOf("roaTTM", "roaRfy"),
        RatioCode.ROI to listOf("roiTTM", "roiAnnual"),
        RatioCode.GROSS_MARGIN to listOf("grossMarginTTM", "grossMarginAnnual"),
        RatioCode.OPERATING_MARGIN to listOf("operatingMarginTTM", "operatingMarginAnnual"),
        RatioCode.PRETAX_MARGIN to listOf("pretaxMarginTTM", "pretaxMarginAnnual"),
        RatioCode.NET_MARGIN to listOf("netProfitMarginTTM", "netProfitMarginAnnual"),
        RatioCode.REVENUE_GROWTH to listOf("revenueGrowthTTMYoy"),
        RatioCode.REVENUE_GROWTH_5Y to listOf("revenueGrowth5Y"),
        RatioCode.EPS_GROWTH to listOf("epsGrowthTTMYoy"),
        RatioCode.EPS_GROWTH_5Y to listOf("epsGrowth5Y"),
        RatioCode.DIVIDEND_GROWTH_5Y to listOf("dividendGrowthRate5Y"),
        RatioCode.CURRENT_RATIO to listOf("currentRatioQuarterly", "currentRatioAnnual"),
        RatioCode.QUICK_RATIO to listOf("quickRatioQuarterly", "quickRatioAnnual"),
        RatioCode.DEBT_TO_EQUITY to listOf("totalDebt/totalEquityQuarterly", "totalDebt/totalEquityAnnual"),
        RatioCode.LT_DEBT_TO_EQUITY to listOf("longTermDebt/equityQuarterly", "longTermDebt/equityAnnual"),
        RatioCode.INTEREST_COVERAGE to listOf("netInterestCoverageTTM", "netInterestCoverageAnnual"),
        RatioCode.ASSET_TURNOVER to listOf("assetTurnoverTTM", "assetTurnoverAnnual"),
        RatioCode.INVENTORY_TURNOVER to listOf("inventoryTurnoverTTM", "inventoryTurnoverAnnual"),
        RatioCode.RECEIVABLES_TURNOVER to listOf("receivablesTurnoverTTM", "receivablesTurnoverAnnual"),
        RatioCode.DIVIDEND_YIELD to listOf("currentDividendYieldTTM", "dividendYieldIndicatedAnnual"),
        RatioCode.PAYOUT_RATIO to listOf("payoutRatioTTM", "payoutRatioAnnual"),
        RatioCode.BETA to listOf("beta"),
        RatioCode.EPS to listOf("epsTTM", "epsExclExtraItemsTTM", "epsBasicExclExtraItemsTTM"),
        RatioCode.BOOK_VALUE_PER_SHARE to listOf("bookValuePerShareQuarterly", "bookValuePerShareAnnual"),
    )

    fun searchHits(response: SearchResponseDto): List<SearchHit> =
        response.result.mapNotNull { result ->
            val symbol = result.symbol?.let(Symbol::parseOrNull) ?: return@mapNotNull null
            SearchHit(symbol = symbol, name = result.description.orBlankTo(symbol.value), type = result.type.nullIfBlank())
        }

    fun quote(response: QuoteDto, currency: String): Quote? {
        val price = response.c?.takeIf { it > 0 } ?: return null
        if (response.t == 0L) return null
        return Quote(price = price, currency = currency)
    }

    fun ratios(response: BasicFinancialsDto, requested: Symbol): Ratios? {
        val metric = response.metric?.takeIf { it.isNotEmpty() } ?: return null
        val perShareValid = response.symbol.nullIfBlank()?.equals(requested.value, ignoreCase = true) ?: true
        return Ratios(
            RatioCode.entries.map { code ->
                val usable = perShareValid || code.unit != RatioUnit.CURRENCY
                Ratio(code, if (usable) metricKeys.getValue(code).firstNotNullOfOrNull { metric.number(it) } else null)
            },
        )
    }

    fun companyProfile(response: CompanyProfile2Dto): CompanyProfile? {
        if (response.name.nullIfBlank() == null && response.ticker.nullIfBlank() == null) return null
        return CompanyProfile(
            name = response.name.nullIfBlank(),
            country = response.country.nullIfBlank(),
            currency = response.currency.nullIfBlank(),
            sector = response.finnhubIndustry.nullIfBlank(),
            industry = null,
            ipoDate = response.ipo.toLocalDateOrNull(),
            marketCapitalization = response.marketCapitalization?.times(MILLION),
            sharesOutstanding = response.shareOutstanding?.times(MILLION),
            employees = null,
            website = response.weburl.nullIfBlank(),
            description = null,
            logoUrl = response.logo.nullIfBlank(),
        )
    }

    fun balanceSheet(response: FinancialsReportedDto): BalanceSheet? {
        val periods = response.data
            .filter { (it.quarter ?: ANNUAL_QUARTER) == ANNUAL_QUARTER }
            .mapNotNull(::balanceSheetPeriod)
            .sortedWith(compareByDescending<BalanceSheetPeriod> { it.periodEnd }.thenByDescending { it.filedAt })
            .distinctBy { it.periodEnd }
        return periods.takeIf { it.isNotEmpty() }?.let(::BalanceSheet)
    }

    fun listedSymbol(dto: StockSymbolDto): ListedSymbol? {
        val symbol = dto.symbol?.let(Symbol::parseOrNull) ?: return null
        val currency = dto.currency.nullIfBlank() ?: return null
        return ListedSymbol(
            symbol = symbol,
            name = dto.description.orBlankTo(symbol.value),
            mic = dto.mic.nullIfBlank(),
            currency = currency,
            type = dto.type.nullIfBlank(),
        )
    }

    private fun balanceSheetPeriod(report: ReportDto): BalanceSheetPeriod? {
        val periodEnd = report.endDate.toLocalDateOrNull() ?: return null
        val items = balanceSheetItems(report.report?.bs).takeIf { it.isNotEmpty() } ?: return null
        return BalanceSheetPeriod(
            periodEnd = periodEnd,
            fiscalYear = report.year ?: periodEnd.year,
            fiscalPeriod = FiscalPeriod.FY,
            filedAt = report.filedDate.toLocalDateOrNull(),
            form = report.form.nullIfBlank(),
            items = items,
        )
    }

    private fun balanceSheetItems(bs: JsonElement?): List<BalanceSheetItem> = when (bs) {
        is JsonArray -> bs.mapNotNull { (it as? JsonObject)?.let(::reportedItem) }
        is JsonObject -> bs.mapNotNull { (concept, value) ->
            (value as? JsonPrimitive)?.numberOrNull()?.let { BalanceSheetItem(concept, concept, it, unit = null) }
        }
        else -> emptyList()
    }

    private fun reportedItem(item: JsonObject): BalanceSheetItem? {
        val concept = item.string("concept") ?: return null
        val value = item.number("value") ?: return null
        return BalanceSheetItem(
            concept = concept,
            label = item.string("label") ?: concept,
            value = value,
            unit = item.string("unit"),
        )
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.numberOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.nullIfBlank()

    private fun JsonPrimitive.numberOrNull(): Double? =
        takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.orBlankTo(fallback: String): String = nullIfBlank() ?: fallback

    private fun String?.toLocalDateOrNull(): LocalDate? =
        nullIfBlank()?.take(ISO_DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
