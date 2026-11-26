"""JSON contract of the proxy, independent of yfinance result shapes. Field names are camelCase in JSON."""

from datetime import date, datetime

import msgspec


class _Contract(msgspec.Struct, kw_only=True, rename="camel"):
    pass


class Quote(_Contract):
    """Latest price of a symbol in the main currency unit."""

    symbol: str
    price: float
    previous_close: float | None
    currency: str
    market_time: datetime | None
    exchange: str | None


class Statistics(_Contract):
    """Key statistics: ratios in Yahoo units and amounts in `currency`, the main unit of the quote currency."""

    symbol: str
    currency: str | None
    ratios: dict[str, float]
    amounts: dict[str, float]


class Profile(_Contract):
    """Company profile."""

    symbol: str
    name: str | None
    quote_type: str | None
    type_display: str | None
    exchange: str | None
    currency: str | None
    financial_currency: str | None
    sector: str | None
    industry: str | None
    country: str | None
    city: str | None
    website: str | None
    description: str | None
    employees: int | None


class Dividend(_Contract):
    """One dividend per share, by ex-date in the exchange's time zone."""

    ex_date: date
    amount: float


class Dividends(_Contract):
    """Dividend history, oldest first, in the main unit of the quote currency."""

    symbol: str
    currency: str | None
    dividends: list[Dividend]


class BalanceSheetYear(_Contract):
    """One annual balance sheet: item keys (camelCase Yahoo item names) with their values."""

    fiscal_year_end: date
    items: dict[str, float]


class BalanceSheet(_Contract):
    """Annual balance sheets, newest first, in the main unit of the financial statements currency."""

    symbol: str
    currency: str | None
    years: list[BalanceSheetYear]


class ErrorBody(_Contract):
    """Body of every response outside 2XX."""

    error: str
    message: str


class Health(_Contract):
    """Body of the health endpoint."""

    status: str
