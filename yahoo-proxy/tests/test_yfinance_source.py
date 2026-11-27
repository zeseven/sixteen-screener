import math
from datetime import date

import pandas as pd
import pytest
from curl_cffi.requests.exceptions import HTTPError, Timeout
from yfinance.exceptions import YFException, YFRateLimitError, YFTickerMissingError

from tests.support import FailingTicker, RecordedTicker, http_error, load_fixture
from yahoo_proxy.errors import YahooErrorKind
from yahoo_proxy.yfinance_source import YfinanceSource, classify_yfinance_error


class TestYfinanceSource:
    def test_price_metadata_loads_history_first(self) -> None:
        ticker = RecordedTicker(load_fixture("AAPL"))

        metadata = YfinanceSource(lambda _symbol: ticker).price_metadata("AAPL")

        assert ticker.history_calls == 1
        assert metadata["regularMarketPrice"] == 355.58

    def test_each_call_uses_a_fresh_ticker(self) -> None:
        requested: list[str] = []

        def factory(symbol: str) -> RecordedTicker:
            requested.append(symbol)
            return RecordedTicker(load_fixture(symbol))

        source = YfinanceSource(factory)
        source.info("AAPL")
        source.info("AAPL")

        assert requested == ["AAPL", "AAPL"]

    def test_dividends_use_local_ex_dates_and_quote_currency(self) -> None:
        dividends = YfinanceSource(lambda _symbol: RecordedTicker(load_fixture("SAP.DE"))).dividends("SAP.DE")

        assert dividends.currency == "EUR"
        assert dividends.payments[-1].ex_date == date(2026, 5, 6)
        assert dividends.payments[-1].amount == 2.5

    def test_dividends_skip_non_finite_amounts(self) -> None:
        class TickerWithGap(RecordedTicker):
            def get_dividends(self, period: str = "max") -> pd.Series[float]:
                return pd.Series(
                    [1.0, math.nan],
                    index=pd.Index(
                        [pd.Timestamp("2025-01-02T09:00:00+01:00"), pd.Timestamp("2025-06-02T09:00:00+02:00")]
                    ),
                    dtype=float,
                )

        dividends = YfinanceSource(lambda _symbol: TickerWithGap(load_fixture("SAP.DE"))).dividends("SAP.DE")

        assert [payment.ex_date for payment in dividends.payments] == [date(2025, 1, 2)]

    def test_balance_sheet_drops_missing_values(self) -> None:
        columns = YfinanceSource(lambda _symbol: RecordedTicker(load_fixture("SHEL.L"))).balance_sheet("SHEL.L")

        assert {column.fiscal_year_end for column in columns} >= {date(2025, 12, 31)}
        assert all(not math.isnan(value) for column in columns for value in column.items.values())

    def test_balance_sheet_does_not_read_info(self) -> None:
        class BalanceSheetOnlyTicker(FailingTicker):
            @property
            def balance_sheet(self) -> pd.DataFrame:
                return pd.DataFrame()

        source = YfinanceSource(lambda _symbol: BalanceSheetOnlyTicker(AssertionError("info must not be read")))

        assert source.balance_sheet("X") == ()

    def test_financial_currency_comes_from_the_quote_summary(self) -> None:
        source = YfinanceSource(lambda _symbol: RecordedTicker(load_fixture("SHEL.L")))

        assert source.financial_currency("SHEL.L") == "USD"

    def test_unexpected_date_label_raises(self) -> None:
        class TickerWithTextLabels(RecordedTicker):
            def get_dividends(self, period: str = "max") -> pd.Series[float]:
                return pd.Series([1.0], index=pd.Index(["not a date"]), dtype=float)

        source = YfinanceSource(lambda _symbol: TickerWithTextLabels(load_fixture("SAP.DE")))

        with pytest.raises(ValueError, match="Expected a date label"):
            source.dividends("SAP.DE")


class TestClassifyYfinanceError:
    @pytest.mark.parametrize(
        ("error", "expected"),
        [
            (YFRateLimitError(), YahooErrorKind.RATE_LIMITED),
            (YFTickerMissingError("XXX.DE", "no timezone found"), YahooErrorKind.NOT_FOUND),
            (http_error(404), YahooErrorKind.NOT_FOUND),
            (http_error(429), YahooErrorKind.RATE_LIMITED),
            (http_error(500), YahooErrorKind.UPSTREAM_FAILURE),
            (HTTPError("no response"), YahooErrorKind.UPSTREAM_FAILURE),
            (Timeout("timed out"), YahooErrorKind.TIMEOUT),
            (TimeoutError(), YahooErrorKind.TIMEOUT),
            (YFException("other"), YahooErrorKind.UPSTREAM_FAILURE),
            (KeyError("regularMarketPrice"), YahooErrorKind.UPSTREAM_FAILURE),
            (ValueError("Expected a date label"), YahooErrorKind.UPSTREAM_FAILURE),
        ],
    )
    def test_maps_exceptions_to_error_kinds(self, error: Exception, expected: YahooErrorKind) -> None:
        assert classify_yfinance_error(error).kind is expected

    def test_upstream_status_is_in_the_message(self) -> None:
        assert "502" in classify_yfinance_error(http_error(502)).message

    def test_unexpected_errors_are_logged_with_traceback(self, caplog: pytest.LogCaptureFixture) -> None:
        classify_yfinance_error(KeyError("regularMarketPrice"))

        assert any(record.exc_info is not None for record in caplog.records)
