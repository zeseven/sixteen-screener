"""Use cases of the proxy: Yahoo reads through the caller, mapped to the contract."""

from rusty_results.prelude import Err, Ok, Result

from yahoo_proxy.caller import Deadline, YahooCaller
from yahoo_proxy.contract import BalanceSheet, Dividends, Profile, Quote, Statistics
from yahoo_proxy.errors import YahooError
from yahoo_proxy.mapping import map_balance_sheet, map_dividends, map_profile, map_quote, map_statistics
from yahoo_proxy.source import YahooBalanceSheet, YahooBalanceSheetColumn, YahooSource


class YahooService:
    """Reads Yahoo data of one symbol through the caller's error boundary, one caller call per Yahoo read."""

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
        """Annual balance sheets; the financial currency is a second Yahoo read, made only when there are columns.

        Both reads share one deadline, so the whole request ends within the timeout.
        """
        deadline = self._caller.deadline()
        columns_result = await self._caller.call(lambda: self._source.balance_sheet(symbol), deadline)
        if columns_result.is_err:
            return Err(columns_result.unwrap_err())
        columns = columns_result.unwrap()
        currency = await self._financial_currency(symbol, columns, deadline)
        return currency.and_then(
            lambda financial_currency: map_balance_sheet(symbol, YahooBalanceSheet(columns, financial_currency))
        )

    async def _financial_currency(
        self, symbol: str, columns: tuple[YahooBalanceSheetColumn, ...], deadline: Deadline
    ) -> Result[str | None, YahooError]:
        if not columns:
            return Ok(None)
        return await self._caller.call(lambda: self._source.financial_currency(symbol), deadline)
