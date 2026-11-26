"""Mapping of Yahoo records to the proxy contract."""

from typing import Final

from rusty_results.prelude import Err, Ok, Result

from yahoo_proxy.contract import BalanceSheet, BalanceSheetYear, Dividend, Dividends, Profile, Quote, Statistics
from yahoo_proxy.currency import CurrencyUnit, currency_unit
from yahoo_proxy.errors import YahooError, YahooErrorKind
from yahoo_proxy.record import YahooRecord, first_text, integer, number, text, timestamp
from yahoo_proxy.source import YahooBalanceSheet, YahooDividends

RATIO_KEYS: Final = (
    "trailingPE",
    "forwardPE",
    "trailingPegRatio",
    "priceToBook",
    "priceToSalesTrailing12Months",
    "enterpriseToRevenue",
    "enterpriseToEbitda",
    "returnOnEquity",
    "returnOnAssets",
    "grossMargins",
    "operatingMargins",
    "ebitdaMargins",
    "profitMargins",
    "revenueGrowth",
    "earningsGrowth",
    "earningsQuarterlyGrowth",
    "currentRatio",
    "quickRatio",
    "debtToEquity",
    "payoutRatio",
    "dividendYield",
    "fiveYearAvgDividendYield",
    "beta",
)

AMOUNT_KEYS: Final = (
    "trailingEps",
    "forwardEps",
    "bookValue",
    "revenuePerShare",
    "totalCashPerShare",
    "dividendRate",
    "marketCap",
)


def map_quote(symbol: str, metadata: YahooRecord) -> Result[Quote, YahooError]:
    """Maps price metadata; a record without price or currency means Yahoo has no price for the symbol."""
    price = number(metadata, "regularMarketPrice")
    currency = text(metadata, "currency")
    if price is None or currency is None:
        return Err(YahooError(YahooErrorKind.NOT_FOUND, f"Yahoo has no price for {symbol}"))
    unit = currency_unit(currency)
    previous_close = number(metadata, "chartPreviousClose")
    return Ok(
        Quote(
            symbol=symbol,
            price=unit.to_main_unit(price),
            previous_close=unit.to_main_unit(previous_close) if previous_close is not None else None,
            currency=unit.code,
            market_time=timestamp(metadata, "regularMarketTime"),
            exchange=first_text(metadata, "fullExchangeName", "exchangeName"),
        )
    )


def map_statistics(symbol: str, info: YahooRecord) -> Result[Statistics, YahooError]:
    """Maps key statistics; amounts are dropped when the quote currency is unknown."""
    if text(info, "quoteType") is None:
        return Err(_no_quote_summary(symbol))
    currency = text(info, "currency")
    return Ok(
        Statistics(
            symbol=symbol,
            currency=currency_unit(currency).code if currency is not None else None,
            ratios=_present_numbers(info, RATIO_KEYS),
            amounts=_present_numbers(info, AMOUNT_KEYS) if currency is not None else {},
        )
    )


def map_profile(symbol: str, info: YahooRecord) -> Result[Profile, YahooError]:
    """Maps the company profile."""
    if text(info, "quoteType") is None:
        return Err(_no_quote_summary(symbol))
    currency = text(info, "currency")
    financial_currency = text(info, "financialCurrency")
    return Ok(
        Profile(
            symbol=symbol,
            name=first_text(info, "longName", "shortName"),
            quote_type=text(info, "quoteType"),
            type_display=text(info, "typeDisp"),
            exchange=first_text(info, "fullExchangeName", "exchange"),
            currency=currency_unit(currency).code if currency is not None else None,
            financial_currency=currency_unit(financial_currency).code if financial_currency is not None else None,
            sector=text(info, "sector"),
            industry=text(info, "industry"),
            country=text(info, "country"),
            city=text(info, "city"),
            website=text(info, "website"),
            description=text(info, "longBusinessSummary"),
            employees=integer(info, "fullTimeEmployees"),
        )
    )


def map_dividends(symbol: str, history: YahooDividends) -> Result[Dividends, YahooError]:
    """Maps the dividend history; an empty history is valid (the company pays no dividends)."""
    if history.currency is None and history.payments:
        return Err(
            YahooError(YahooErrorKind.UPSTREAM_FAILURE, f"Yahoo returned dividends of {symbol} without currency")
        )
    unit = currency_unit(history.currency) if history.currency is not None else None
    payments = sorted(history.payments, key=lambda payment: payment.ex_date)
    return Ok(
        Dividends(
            symbol=symbol,
            currency=unit.code if unit is not None else None,
            dividends=[
                Dividend(ex_date=payment.ex_date, amount=_in_main_unit(payment.amount, unit)) for payment in payments
            ],
        )
    )


def map_balance_sheet(symbol: str, sheet: YahooBalanceSheet) -> Result[BalanceSheet, YahooError]:
    """Maps annual balance sheets, newest first; years without any value are left out."""
    unit = currency_unit(sheet.financial_currency) if sheet.financial_currency is not None else None
    years = [
        BalanceSheetYear(
            fiscal_year_end=column.fiscal_year_end,
            items={item_key(label): _in_main_unit(value, unit) for label, value in column.items.items()},
        )
        for column in sorted(sheet.columns, key=lambda column: column.fiscal_year_end, reverse=True)
        if column.items
    ]
    if not years:
        return Err(YahooError(YahooErrorKind.NOT_FOUND, f"Yahoo has no balance sheet for {symbol}"))
    return Ok(BalanceSheet(symbol=symbol, currency=unit.code if unit is not None else None, years=years))


def item_key(label: str) -> str:
    """Turns a yfinance row label (`Total Assets`) into a camelCase key (`totalAssets`)."""
    compact = "".join(label.split())
    return compact[:1].lower() + compact[1:]


def _present_numbers(record: YahooRecord, keys: tuple[str, ...]) -> dict[str, float]:
    return {key: value for key in keys if (value := number(record, key)) is not None}


def _in_main_unit(amount: float, unit: CurrencyUnit | None) -> float:
    return unit.to_main_unit(amount) if unit is not None else amount


def _no_quote_summary(symbol: str) -> YahooError:
    return YahooError(YahooErrorKind.NOT_FOUND, f"Yahoo has no quote summary for {symbol}")
