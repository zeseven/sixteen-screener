package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.BalanceSheetItem
import com.sixteenscreener.app.domain.model.BalanceSheetPeriod
import com.sixteenscreener.app.domain.model.CompanyProfile
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Dividend
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.FiscalPeriod
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.Ratio
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import java.time.Instant
import java.time.LocalDate

object CompanyData {
    val FETCHED_AT: Instant = Instant.parse("2026-11-14T10:00:00Z")

    fun meta(
        source: SourceId = SourceId.FINNHUB,
        status: DataStatus = DataStatus.CACHED,
        fetchedAt: Instant? = FETCHED_AT,
    ) = SourceDataMeta(source = source, status = status, fetchedAt = fetchedAt)

    fun item(concept: String, value: Double, label: String = concept, unit: String? = "USD") =
        BalanceSheetItem(concept = concept, label = label, value = value, unit = unit)

    fun period(
        fiscalYear: Int,
        vararg items: BalanceSheetItem,
        fiscalPeriod: FiscalPeriod = FiscalPeriod.FY,
    ) = BalanceSheetPeriod(
        periodEnd = LocalDate.of(fiscalYear, 9, 30),
        fiscalYear = fiscalYear,
        fiscalPeriod = fiscalPeriod,
        filedAt = null,
        form = null,
        items = items.toList(),
    )

    fun balanceSheet(
        vararg periods: BalanceSheetPeriod,
        source: SourceId = SourceId.FINNHUB,
        status: DataStatus = DataStatus.CACHED,
    ) = BalanceSheet(meta = meta(source = source, status = status), periods = periods.toList())

    fun dividend(exDate: LocalDate, amount: Double, currency: String = "USD", paymentDate: LocalDate? = null) = Dividend(
        exDate = exDate,
        paymentDate = paymentDate,
        recordDate = null,
        declarationDate = null,
        amount = amount,
        currency = currency,
    )

    fun dividends(
        vararg dividends: Dividend,
        dividendYield: Double? = 0.44,
        status: DataStatus = DataStatus.CACHED,
    ) = Dividends(meta = meta(status = status), dividendYield = dividendYield, dividends = dividends.toList())

    fun priceAndRatios(
        quote: Quote? = Companies.quote(),
        ratios: List<Ratio> = listOf(Ratio("PE", "P/E", RatioUnit.RATIO, 31.25), Ratio("PB", "P/B", RatioUnit.RATIO, null)),
        status: DataStatus = DataStatus.CACHED,
        indicators: List<Indicator> = emptyList(),
    ) = PriceAndRatios(meta = meta(status = status), quote = quote, ratios = ratios, indicators = indicators)

    fun companyProfile(
        symbol: String = "AAPL",
        name: String = "Apple Inc.",
        exchange: String = "NASDAQ",
        market: Market? = Market.US,
        sector: String? = "Technology",
        type: String? = "Common Stock",
        stock: Boolean = true,
        homeExchange: Boolean = false,
    ) = CompanyProfile(
        symbol = Symbol(symbol),
        ticker = symbol.substringBefore('.'),
        exchange = exchange,
        name = name,
        market = market,
        type = type,
        stock = stock,
        homeExchange = homeExchange,
        country = "US",
        currency = "USD",
        sector = sector,
        industry = "Consumer Electronics",
        ipoDate = LocalDate.of(1980, 12, 12),
        marketCapitalization = 3.5e12,
        sharesOutstanding = 15_200_000_000.0,
        employees = 164_000,
        website = "https://www.apple.com",
        description = "Designs smartphones.",
    )

    fun companyProfileData(
        profile: CompanyProfile? = companyProfile(),
        status: DataStatus = DataStatus.CACHED,
    ) = CompanyProfileData(meta = meta(status = status, fetchedAt = FETCHED_AT.takeIf { profile != null }), profile = profile)
}
