"""Replays recorded yfinance results as pandas objects, the way yfinance returns them."""

from collections.abc import Callable, Mapping
from pathlib import Path
from types import SimpleNamespace
from typing import Final

import msgspec
import pandas as pd
from curl_cffi.requests.exceptions import HTTPError

from yahoo_proxy.yfinance_source import TickerLike

FIXTURES_DIR: Final = Path(__file__).resolve().parent / "fixtures"


class RecordedDividend(msgspec.Struct, rename="camel"):
    """A recorded dividend."""

    ex_date: str
    amount: float


class Fixture(msgspec.Struct, rename="camel"):
    """Recorded yfinance results of one symbol (see scripts/record_fixtures.py)."""

    symbol: str
    yfinance_version: str
    info: dict[str, object]
    price_metadata: dict[str, object]
    dividend_currency: str | None
    dividends: list[RecordedDividend]
    balance_sheet: dict[str, dict[str, float | None]]


def load_fixture(symbol: str) -> Fixture:
    """Loads the recorded results of a symbol."""
    return msgspec.json.decode((FIXTURES_DIR / f"{symbol}.json").read_bytes(), type=Fixture)


class RecordedTicker:
    """A ticker answering with recorded results."""

    def __init__(self, fixture: Fixture) -> None:
        self._fixture = fixture
        self.history_calls = 0

    @property
    def info(self) -> Mapping[str, object]:
        """Recorded quote summary."""
        return self._fixture.info

    @property
    def balance_sheet(self) -> pd.DataFrame:
        """Recorded balance sheets with missing values as NaN."""
        return pd.DataFrame(
            {
                pd.Timestamp(fiscal_year_end): pd.Series(
                    {label: float("nan") if value is None else value for label, value in items.items()},
                    dtype=float,
                )
                for fiscal_year_end, items in self._fixture.balance_sheet.items()
            }
        )

    def history(self, *, period: str, interval: str, auto_adjust: bool) -> pd.DataFrame:
        """Counts the call; the proxy only needs the metadata it loads."""
        self.history_calls += 1
        return pd.DataFrame()

    def get_history_metadata(self) -> Mapping[str, object]:
        """Recorded price metadata with the market time as a pandas timestamp."""
        metadata = dict(self._fixture.price_metadata)
        market_time = metadata.get("regularMarketTime")
        if isinstance(market_time, str):
            metadata["regularMarketTime"] = pd.Timestamp(market_time)
        return metadata

    def get_dividends(self, period: str = "max") -> pd.Series[float]:
        """Recorded dividends indexed by ex-date."""
        return pd.Series(
            [dividend.amount for dividend in self._fixture.dividends],
            index=pd.Index([pd.Timestamp(dividend.ex_date) for dividend in self._fixture.dividends]),
            dtype=float,
        )


class FailingTicker:
    """A ticker that raises the given exception on every read."""

    def __init__(self, error: Exception) -> None:
        self._error = error

    @property
    def info(self) -> Mapping[str, object]:
        """Raises."""
        raise self._error

    @property
    def balance_sheet(self) -> pd.DataFrame:
        """Raises."""
        raise self._error

    def history(self, *, period: str, interval: str, auto_adjust: bool) -> pd.DataFrame:
        """Raises."""
        raise self._error

    def get_history_metadata(self) -> Mapping[str, object]:
        """Raises."""
        raise self._error

    def get_dividends(self, period: str = "max") -> pd.Series[float]:
        """Raises."""
        raise self._error


def recorded_tickers() -> Callable[[str], TickerLike]:
    """A ticker factory answering every symbol with its recorded results."""
    return lambda symbol: RecordedTicker(load_fixture(symbol))


def failing_tickers(error: Exception) -> Callable[[str], TickerLike]:
    """A ticker factory whose tickers raise the given exception."""
    return lambda _symbol: FailingTicker(error)


def http_error(status_code: int) -> HTTPError:
    """An HTTP error of the yfinance HTTP client with the given response status."""
    return HTTPError(f"HTTP Error {status_code}", 0, SimpleNamespace(status_code=status_code))
