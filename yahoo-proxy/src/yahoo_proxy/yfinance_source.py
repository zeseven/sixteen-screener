"""Yahoo source backed by the yfinance library; the only module that knows yfinance and its exceptions."""

import logging
import math
from collections.abc import Callable, Hashable, Iterator, Mapping
from datetime import date, datetime
from pathlib import Path
from typing import Protocol

import pandas as pd
import yfinance  # noqa: TID251
from curl_cffi.requests.exceptions import HTTPError, Timeout
from yfinance.exceptions import YFRateLimitError, YFTickerMissingError  # noqa: TID251

from yahoo_proxy.errors import YahooError, YahooErrorKind
from yahoo_proxy.record import YahooRecord, text
from yahoo_proxy.source import YahooBalanceSheetColumn, YahooDividend, YahooDividends

_logger = logging.getLogger(__name__)


class TickerLike(Protocol):
    """The part of `yfinance.Ticker` the source uses."""

    @property
    def info(self) -> Mapping[str, object]:
        """Quote summary record."""
        ...

    @property
    def balance_sheet(self) -> pd.DataFrame:
        """Annual balance sheets: one column per fiscal year end, one row per item."""
        ...

    def history(self, *, period: str, interval: str, auto_adjust: bool) -> pd.DataFrame:
        """Loads price history and its metadata."""
        ...

    def get_history_metadata(self) -> Mapping[str, object]:
        """Metadata of the loaded price history."""
        ...

    def get_dividends(self, period: str = ...) -> pd.Series[float]:
        """Dividends indexed by ex-date."""
        ...


class YfinanceSource:
    """Reads Yahoo data with a fresh ticker per call, so a failed call never leaves state behind."""

    def __init__(self, ticker_factory: Callable[[str], TickerLike]) -> None:
        self._ticker_factory = ticker_factory

    def price_metadata(self, symbol: str) -> YahooRecord:
        """Returns the metadata of the latest daily prices."""
        ticker = self._ticker_factory(symbol)
        ticker.history(period="5d", interval="1d", auto_adjust=False)
        return ticker.get_history_metadata()

    def info(self, symbol: str) -> YahooRecord:
        """Returns the quote summary record."""
        return self._ticker_factory(symbol).info

    def dividends(self, symbol: str) -> YahooDividends:
        """Returns the full dividend history with the currency of the quote."""
        ticker = self._ticker_factory(symbol)
        series = ticker.get_dividends(period="max")
        payments = tuple(YahooDividend(_to_date(label), amount) for label, amount in _finite_values(series))
        return YahooDividends(payments=payments, currency=text(ticker.get_history_metadata(), "currency"))

    def balance_sheet(self, symbol: str) -> tuple[YahooBalanceSheetColumn, ...]:
        """Returns the annual balance sheets."""
        return tuple(
            YahooBalanceSheetColumn(
                fiscal_year_end=_to_date(label),
                items={str(item): value for item, value in _finite_values(column)},
            )
            for label, column in self._ticker_factory(symbol).balance_sheet.items()
        )

    def financial_currency(self, symbol: str) -> str | None:
        """Returns the currency of the financial statements from the quote summary."""
        return text(self._ticker_factory(symbol).info, "financialCurrency")


def create_yfinance_source(cache_dir: Path) -> YfinanceSource:
    """Configures yfinance to raise its errors and keep its cache in `cache_dir`, and returns the source."""
    yfinance.config.debug.hide_exceptions = False
    yfinance.config.network.retries = 0
    cache_dir.mkdir(parents=True, exist_ok=True)
    yfinance.set_tz_cache_location(str(cache_dir))
    return YfinanceSource(yfinance.Ticker)


def classify_yfinance_error(error: Exception) -> YahooError:
    """Maps an exception raised by yfinance or its HTTP client to a Yahoo error."""
    match error:
        case YFRateLimitError():
            return YahooError(YahooErrorKind.RATE_LIMITED, "Yahoo rate-limited the request")
        case YFTickerMissingError():
            return YahooError(YahooErrorKind.NOT_FOUND, f"Yahoo has no data: {error}")
        case HTTPError():
            return _classify_http_error(error)
        case Timeout() | TimeoutError():
            return YahooError(YahooErrorKind.TIMEOUT, "Yahoo did not answer in time")
        case _:
            _logger.exception("Unexpected yfinance failure", exc_info=error)
            return YahooError(YahooErrorKind.UPSTREAM_FAILURE, f"Unexpected Yahoo result: {type(error).__name__}")


def _classify_http_error(error: HTTPError) -> YahooError:
    status = _status_code(error)
    match status:
        case 404:
            return YahooError(YahooErrorKind.NOT_FOUND, "Yahoo has no data for the symbol")
        case 429:
            return YahooError(YahooErrorKind.RATE_LIMITED, "Yahoo rate-limited the request")
        case _:
            return YahooError(YahooErrorKind.UPSTREAM_FAILURE, f"Yahoo answered with status {status}")


def _status_code(error: HTTPError) -> int | None:
    response: object = getattr(error, "response", None)
    status: object = getattr(response, "status_code", None)
    return status if isinstance(status, int) else None


def _finite_values(series: pd.Series[float]) -> Iterator[tuple[Hashable, float]]:
    for label, value in series.items():
        if isinstance(value, int | float) and not isinstance(value, bool) and math.isfinite(value):
            yield label, float(value)


def _to_date(label: Hashable) -> date:
    if isinstance(label, datetime):
        return label.date()
    message = f"Expected a date label, got {label!r}"
    raise ValueError(message)
