"""Use cases of the proxy: one Yahoo read per request, mapped to the contract."""

from rusty_results.prelude import Result

from yahoo_proxy.caller import YahooCaller
from yahoo_proxy.contract import BalanceSheet, Dividends, Profile, Quote, Statistics
from yahoo_proxy.errors import YahooError
from yahoo_proxy.mapping import map_balance_sheet, map_dividends, map_profile, map_quote, map_statistics
from yahoo_proxy.source import YahooSource


class YahooService:
    """Reads Yahoo data of one symbol through the caller's error boundary."""

    def __init__(self, source: YahooSource, caller: YahooCaller) -> None:
        self._source = source
        self._caller = caller

    async def quote(self, symbol: str) -> Result[Quote, YahooError]:
        """Latest price."""
        metadata = await self._caller.call(lambda: self._source.price_metadata(symbol))
        return metadata.and_then(lambda record: map_quote(symbol, record))

    async def statistics(self, symbol: str) -> Result[Statistics, YahooError]:
        """Key statistics."""
        info = await self._caller.call(lambda: self._source.info(symbol))
        return info.and_then(lambda record: map_statistics(symbol, record))

    async def profile(self, symbol: str) -> Result[Profile, YahooError]:
        """Company profile."""
        info = await self._caller.call(lambda: self._source.info(symbol))
        return info.and_then(lambda record: map_profile(symbol, record))

    async def dividends(self, symbol: str) -> Result[Dividends, YahooError]:
        """Dividend history."""
        history = await self._caller.call(lambda: self._source.dividends(symbol))
        return history.and_then(lambda payments: map_dividends(symbol, payments))

    async def balance_sheet(self, symbol: str) -> Result[BalanceSheet, YahooError]:
        """Annual balance sheets."""
        sheet = await self._caller.call(lambda: self._source.balance_sheet(symbol))
        return sheet.and_then(lambda columns: map_balance_sheet(symbol, columns))
