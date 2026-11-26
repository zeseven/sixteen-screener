package com.sixteenscreener.adapter.outbound.yahoo

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividend
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import java.time.LocalDate
import java.util.Locale

internal object YahooMapper {
    private const val PERCENT_PER_FRACTION = 100.0

    private val asIs: (Double) -> Double = { it }
    private val fractionToPercent: (Double) -> Double = { it * PERCENT_PER_FRACTION }
    private val percentToRatio: (Double) -> Double = { it / PERCENT_PER_FRACTION }

    /** Yahoo key statistics (`ratios` of the proxy) mapped to shared ratio codes, with the conversion into the shared unit. */
    val ratioKeys: Map<RatioCode, Pair<String, (Double) -> Double>> = mapOf(
        RatioCode.PE to ("trailingPE" to asIs),
        RatioCode.PB to ("priceToBook" to asIs),
        RatioCode.PS to ("priceToSalesTrailing12Months" to asIs),
        RatioCode.EV_EBITDA to ("enterpriseToEbitda" to asIs),
        RatioCode.EV_REVENUE to ("enterpriseToRevenue" to asIs),
        RatioCode.PEG to ("trailingPegRatio" to asIs),
        RatioCode.ROE to ("returnOnEquity" to fractionToPercent),
        RatioCode.ROA to ("returnOnAssets" to fractionToPercent),
        RatioCode.GROSS_MARGIN to ("grossMargins" to fractionToPercent),
        RatioCode.OPERATING_MARGIN to ("operatingMargins" to fractionToPercent),
        RatioCode.NET_MARGIN to ("profitMargins" to fractionToPercent),
        RatioCode.REVENUE_GROWTH to ("revenueGrowth" to fractionToPercent),
        RatioCode.EPS_GROWTH to ("earningsGrowth" to fractionToPercent),
        RatioCode.CURRENT_RATIO to ("currentRatio" to asIs),
        RatioCode.QUICK_RATIO to ("quickRatio" to asIs),
        RatioCode.DEBT_TO_EQUITY to ("debtToEquity" to percentToRatio),
        RatioCode.DIVIDEND_YIELD to ("dividendYield" to asIs),
        RatioCode.PAYOUT_RATIO to ("payoutRatio" to fractionToPercent),
        RatioCode.BETA to ("beta" to asIs),
    )

    /** Yahoo per share amounts (`amounts` of the proxy, in the statistics currency) mapped to shared ratio codes. */
    val amountKeys: Map<RatioCode, String> = mapOf(
        RatioCode.EPS to "trailingEps",
        RatioCode.BOOK_VALUE_PER_SHARE to "bookValue",
    )

    private val securityTypes: Map<String, String> = mapOf(
        "EQUITY" to "Common Stock",
        "ETF" to "ETF",
        "MUTUALFUND" to "Fund",
    )

    private val countryCodesByName: Map<String, String> =
        Locale.getISOCountries().associateBy { Locale.of("", it).getDisplayCountry(Locale.ENGLISH).lowercase() } +
            mapOf(
                "united states" to "US",
                "united kingdom" to "GB",
                "south korea" to "KR",
                "taiwan" to "TW",
                "hong kong" to "HK",
                "macau" to "MO",
                "czech republic" to "CZ",
                "turkey" to "TR",
                "russia" to "RU",
                "bahamas" to "BS",
                "netherlands" to "NL",
            )

    private val labelWordBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

    fun quote(response: QuoteDto, fallbackCurrency: String): Quote? {
        val price = response.price?.takeIf { it.isFinite() && it > 0 } ?: return null
        return Quote(price = price, currency = response.currency.nullIfBlank() ?: fallbackCurrency)
    }

    fun ratios(response: StatisticsDto): Ratios? {
        val ratios = ratioKeys.mapValues { (_, key) ->
            val (name, toSharedUnit) = key
            response.ratios[name]?.takeIf { it.isFinite() }?.let(toSharedUnit)
        }
        val amounts = if (response.currency.nullIfBlank() == null) {
            emptyMap()
        } else {
            amountKeys.mapValues { (_, name) -> response.amounts[name]?.takeIf { it.isFinite() } }
        }
        val values = ratios + amounts
        return values.takeIf { it.values.any { value -> value != null } }?.let(Ratios::of)
    }

    fun dividends(response: DividendsDto): Dividends {
        val currency = response.currency.nullIfBlank() ?: return Dividends(emptyList())
        return Dividends(
            response.dividends.mapNotNull { dividend(it, currency) }.sortedByDescending { it.exDate },
        )
    }

    fun companyProfile(response: ProfileDto): CompanyProfile? {
        val profile = CompanyProfile(
            name = response.name.nullIfBlank(),
            country = countryCodeOf(response.country),
            currency = response.currency.nullIfBlank(),
            sector = response.sector.nullIfBlank(),
            industry = response.industry.nullIfBlank(),
            ipoDate = null,
            marketCapitalization = null,
            sharesOutstanding = null,
            employees = response.employees?.takeIf { it >= 0 },
            website = response.website.nullIfBlank(),
            description = response.description.nullIfBlank(),
            securityType = securityTypeOf(response.quoteType, response.typeDisplay),
        )
        return profile.takeUnless { it == EMPTY_PROFILE }
    }

    fun balanceSheet(response: BalanceSheetDto): BalanceSheet? {
        val periods = response.years
            .mapNotNull { balanceSheetPeriod(it, response.currency.nullIfBlank()) }
            .sortedByDescending { it.periodEnd }
            .distinctBy { it.periodEnd }
        return periods.takeIf { it.isNotEmpty() }?.let(::BalanceSheet)
    }

    /** Security type of a Yahoo [quoteType]: shared names for stocks, ETFs and funds, otherwise Yahoo's [typeDisplay]. */
    fun securityTypeOf(quoteType: String?, typeDisplay: String?): String? =
        quoteType.nullIfBlank()?.uppercase()?.let(securityTypes::get) ?: typeDisplay.nullIfBlank()

    /** ISO 3166-1 alpha-2 code of the English country name [name] Yahoo returns; null for an unknown name. */
    fun countryCodeOf(name: String?): String? = name.nullIfBlank()?.lowercase()?.let(countryCodesByName::get)

    /** Readable label of a camelCase Yahoo balance sheet item key (`netPPE` is `Net PPE`), as Yahoo names the item. */
    fun labelOf(key: String): String =
        key.split(labelWordBoundary).joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

    private fun balanceSheetPeriod(year: BalanceSheetYearDto, currency: String?): BalanceSheetPeriod? {
        val periodEnd = year.fiscalYearEnd.toLocalDateOrNull() ?: return null
        val items = year.items
            .filterValues { it.isFinite() }
            .map { (key, value) -> BalanceSheetItem(concept = key, label = labelOf(key), value = value, unit = currency) }
            .takeIf { it.isNotEmpty() } ?: return null
        return BalanceSheetPeriod(
            periodEnd = periodEnd,
            fiscalYear = periodEnd.year,
            fiscalPeriod = FiscalPeriod.FY,
            filedAt = null,
            form = null,
            items = items,
        )
    }

    private fun dividend(dto: DividendDto, currency: String): Dividend? {
        val exDate = dto.exDate.toLocalDateOrNull() ?: return null
        val amount = dto.amount?.takeIf { it.isFinite() && it > 0 } ?: return null
        return Dividend(
            exDate = exDate,
            paymentDate = null,
            recordDate = null,
            declarationDate = null,
            amount = amount,
            currency = currency,
        )
    }

    private val EMPTY_PROFILE = CompanyProfile(
        name = null,
        country = null,
        currency = null,
        sector = null,
        industry = null,
        ipoDate = null,
        marketCapitalization = null,
        sharesOutstanding = null,
        employees = null,
        website = null,
        description = null,
    )

    private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.toLocalDateOrNull(): LocalDate? =
        nullIfBlank()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
