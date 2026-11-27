import asyncio
import time
from collections.abc import Callable

import pandas as pd
from yfinance.exceptions import YFRateLimitError

from tests.support import FakeClock, RecordedTicker, instant_pacer, load_fixture
from yahoo_proxy.caller import YahooCaller
from yahoo_proxy.errors import YahooErrorKind
from yahoo_proxy.service import YahooService
from yahoo_proxy.yfinance_source import TickerLike, YfinanceSource, classify_yfinance_error


class CountingTicker(RecordedTicker):
    """A recorded ticker that counts the Yahoo reads of the balance sheet use case."""

    def __init__(self, symbol: str, reads: list[str], empty_balance_sheet: bool = False) -> None:
        super().__init__(load_fixture(symbol))
        self._reads = reads
        self._empty_balance_sheet = empty_balance_sheet

    @property
    def info(self) -> dict[str, object]:
        self._reads.append("info")
        return dict(super().info)

    @property
    def balance_sheet(self) -> pd.DataFrame:
        self._reads.append("balance_sheet")
        return pd.DataFrame() if self._empty_balance_sheet else super().balance_sheet


class RateLimitedInfoTicker(RecordedTicker):
    @property
    def info(self) -> dict[str, object]:
        raise YFRateLimitError


class SlowTicker(RecordedTicker):
    """A recorded ticker whose balance sheet and quote summary reads take 0.2 s each."""

    @property
    def info(self) -> dict[str, object]:
        time.sleep(0.2)
        return dict(super().info)

    @property
    def balance_sheet(self) -> pd.DataFrame:
        time.sleep(0.2)
        return super().balance_sheet


def service_for(
    ticker_factory: Callable[[str], TickerLike], clock: FakeClock, timeout_seconds: float = 5
) -> YahooService:
    caller = YahooCaller(
        classify_yfinance_error,
        timeout_seconds=timeout_seconds,
        max_concurrent_calls=2,
        pacer=instant_pacer(clock),
    )
    return YahooService(YfinanceSource(ticker_factory), caller)


def test_balance_sheet_is_paced_as_two_reads() -> None:
    clock = FakeClock()
    reads: list[str] = []
    service = service_for(lambda symbol: CountingTicker(symbol, reads), clock)

    result = asyncio.run(service.balance_sheet("SHEL.L"))

    assert result.unwrap().currency == "USD"
    assert reads == ["balance_sheet", "info"]
    assert clock.sleeps == [1.0]


def test_empty_balance_sheet_is_one_read() -> None:
    clock = FakeClock()
    reads: list[str] = []
    service = service_for(lambda symbol: CountingTicker(symbol, reads, empty_balance_sheet=True), clock)

    result = asyncio.run(service.balance_sheet("SHEL.L"))

    assert result.unwrap_err().kind is YahooErrorKind.NOT_FOUND
    assert reads == ["balance_sheet"]
    assert clock.sleeps == []


def test_failed_financial_currency_read_fails_the_balance_sheet() -> None:
    service = service_for(lambda symbol: RateLimitedInfoTicker(load_fixture(symbol)), FakeClock())

    result = asyncio.run(service.balance_sheet("SHEL.L"))

    assert result.unwrap_err().kind is YahooErrorKind.RATE_LIMITED


def test_single_read_endpoints_take_one_turn_each() -> None:
    clock = FakeClock()
    service = service_for(lambda symbol: RecordedTicker(load_fixture(symbol)), clock)

    async def reads() -> None:
        await service.quote("AAPL")
        await service.statistics("AAPL")
        await service.profile("AAPL")
        await service.dividends("AAPL")

    asyncio.run(reads())

    assert clock.sleeps == [1.0, 1.0, 1.0]


def test_balance_sheet_reads_share_one_timeout() -> None:
    service = service_for(lambda symbol: SlowTicker(load_fixture(symbol)), FakeClock(), timeout_seconds=0.3)

    result = asyncio.run(service.balance_sheet("SHEL.L"))

    assert result.unwrap_err().kind is YahooErrorKind.TIMEOUT


def test_single_read_endpoint_gets_the_whole_timeout() -> None:
    service = service_for(lambda symbol: SlowTicker(load_fixture(symbol)), FakeClock(), timeout_seconds=0.3)

    result = asyncio.run(service.profile("SHEL.L"))

    assert result.is_ok
