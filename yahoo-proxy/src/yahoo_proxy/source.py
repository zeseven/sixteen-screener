"""Port to Yahoo data and the plain data types it returns."""

from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date
from typing import Protocol

from yahoo_proxy.record import YahooRecord


@dataclass(frozen=True, slots=True)
class YahooDividend:
    """One dividend as Yahoo reports it, in the currency of the quote."""

    ex_date: date
    amount: float


@dataclass(frozen=True, slots=True)
class YahooDividends:
    """Dividend history of a symbol and the Yahoo currency code of the quote."""

    payments: tuple[YahooDividend, ...]
    currency: str | None


@dataclass(frozen=True, slots=True)
class YahooBalanceSheetColumn:
    """One annual balance sheet: Yahoo row labels with their values, missing values left out."""

    fiscal_year_end: date
    items: Mapping[str, float]


@dataclass(frozen=True, slots=True)
class YahooBalanceSheet:
    """Annual balance sheets of a symbol and the Yahoo currency code of the financial statements."""

    columns: tuple[YahooBalanceSheetColumn, ...]
    financial_currency: str | None


class YahooSource(Protocol):
    """Blocking access to Yahoo data of one symbol per call.

    Implementations raise the exceptions of the underlying library; callers classify them.
    """

    def price_metadata(self, symbol: str) -> YahooRecord:
        """Returns the metadata of the latest daily prices (price, previous close, currency, market time)."""
        ...

    def info(self, symbol: str) -> YahooRecord:
        """Returns the quote summary record (profile and key statistics)."""
        ...

    def dividends(self, symbol: str) -> YahooDividends:
        """Returns the full dividend history."""
        ...

    def balance_sheet(self, symbol: str) -> YahooBalanceSheet:
        """Returns the annual balance sheets, newest first."""
        ...
