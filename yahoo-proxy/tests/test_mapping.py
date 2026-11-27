from datetime import UTC, date, datetime

import pytest
from rusty_results.prelude import Err, Ok, Result

from tests.support import RecordedTicker, load_fixture
from yahoo_proxy.contract import BalanceSheet, Dividends, Profile, Quote, Statistics
from yahoo_proxy.errors import YahooError, YahooErrorKind
from yahoo_proxy.mapping import (
    item_key,
    map_balance_sheet,
    map_dividends,
    map_profile,
    map_quote,
    map_statistics,
)
from yahoo_proxy.source import YahooBalanceSheet, YahooBalanceSheetColumn, YahooDividend, YahooDividends
from yahoo_proxy.yfinance_source import YfinanceSource


def ok[T](result: Result[T, YahooError]) -> T:
    match result:
        case Ok(value):
            return value
        case Err(error):
            pytest.fail(f"Expected Ok, got {error}")


def error_kind[T](result: Result[T, YahooError]) -> YahooErrorKind:
    match result:
        case Ok(value):
            pytest.fail(f"Expected Err, got {value}")
        case Err(error):
            return error.kind


def source_for(symbol: str) -> YfinanceSource:
    return YfinanceSource(lambda _symbol: RecordedTicker(load_fixture(symbol)))


def recorded_balance_sheet(symbol: str) -> YahooBalanceSheet:
    source = source_for(symbol)
    return YahooBalanceSheet(source.balance_sheet(symbol), source.financial_currency(symbol))


class TestQuote:
    def test_london_price_is_converted_from_pence(self) -> None:
        quote: Quote = ok(map_quote("SHEL.L", source_for("SHEL.L").price_metadata("SHEL.L")))

        assert quote.price == pytest.approx(36.16)
        assert quote.previous_close == pytest.approx(35.745)
        assert quote.currency == "GBP"
        assert quote.exchange == "LSE"
        assert quote.market_time == datetime(2026, 11, 26, 14, 30, 13, tzinfo=UTC)

    def test_us_price_is_kept(self) -> None:
        quote = ok(map_quote("AAPL", source_for("AAPL").price_metadata("AAPL")))

        assert quote.price == 355.58
        assert quote.currency == "USD"
        assert quote.exchange == "NasdaqGS"

    def test_falls_back_to_short_exchange_name(self) -> None:
        metadata = {"regularMarketPrice": 10.0, "currency": "EUR", "exchangeName": "GER"}

        assert ok(map_quote("SAP.DE", metadata)).exchange == "GER"

    def test_without_previous_close_and_market_time(self) -> None:
        quote = ok(map_quote("SAP.DE", {"regularMarketPrice": 10.0, "currency": "EUR"}))

        assert quote.previous_close is None
        assert quote.market_time is None

    @pytest.mark.parametrize(
        "metadata",
        [
            {},
            {"currency": "EUR"},
            {"regularMarketPrice": 10.0},
            {"regularMarketPrice": float("nan"), "currency": "EUR"},
        ],
    )
    def test_without_price_or_currency_is_not_found(self, metadata: dict[str, object]) -> None:
        assert error_kind(map_quote("SAP.DE", metadata)) is YahooErrorKind.NOT_FOUND


class TestStatistics:
    def test_london_amounts_are_in_pounds_and_ratios_keep_yahoo_units(self) -> None:
        statistics: Statistics = ok(map_statistics("SHEL.L", load_fixture("SHEL.L").info))

        assert statistics.currency == "GBP"
        assert statistics.amounts["trailingEps"] == 4.3
        assert statistics.amounts["bookValue"] == pytest.approx(25.85746)
        assert statistics.ratios["trailingPE"] == pytest.approx(8.408139, rel=1e-3)
        assert statistics.ratios["dividendYield"] == 3.35
        assert statistics.ratios["returnOnEquity"] == pytest.approx(0.17432)
        assert statistics.ratios["debtToEquity"] == pytest.approx(38.13)

    def test_contains_only_curated_keys(self) -> None:
        statistics = ok(map_statistics("AAPL", load_fixture("AAPL").info))

        assert "trailingAnnualDividendRate" not in statistics.amounts
        assert "fiftyTwoWeekHigh" not in statistics.amounts
        assert "enterpriseValue" not in statistics.amounts
        assert set(statistics.amounts) == {
            "trailingEps",
            "forwardEps",
            "bookValue",
            "revenuePerShare",
            "totalCashPerShare",
            "dividendRate",
            "marketCap",
        }

    def test_missing_and_invalid_values_are_left_out(self) -> None:
        info: dict[str, object] = {
            "quoteType": "EQUITY",
            "currency": "EUR",
            "trailingPE": "Infinity",
            "forwardPE": None,
            "beta": 1.1,
        }

        assert ok(map_statistics("SAP.DE", info)).ratios == {"beta": 1.1}

    def test_amounts_are_dropped_without_currency(self) -> None:
        statistics = ok(map_statistics("SAP.DE", {"quoteType": "EQUITY", "trailingEps": 6.91, "beta": 1.1}))

        assert statistics.currency is None
        assert statistics.amounts == {}
        assert statistics.ratios == {"beta": 1.1}

    def test_without_quote_summary_is_not_found(self) -> None:
        assert error_kind(map_statistics("XXX.DE", {"trailingPegRatio": None})) is YahooErrorKind.NOT_FOUND


class TestProfile:
    def test_maps_company_profile(self) -> None:
        profile: Profile = ok(map_profile("SAP.DE", load_fixture("SAP.DE").info))

        assert profile.name == "SAP SE"
        assert profile.quote_type == "EQUITY"
        assert profile.type_display == "Equity"
        assert profile.sector == "Technology"
        assert profile.country == "Germany"
        assert profile.currency == "EUR"
        assert profile.financial_currency == "EUR"
        assert profile.website == "https://www.sap.com"
        assert profile.employees == 113076
        assert profile.description is not None

    def test_london_currencies_are_main_units(self) -> None:
        profile = ok(map_profile("SHEL.L", load_fixture("SHEL.L").info))

        assert profile.currency == "GBP"
        assert profile.financial_currency == "USD"

    def test_uses_short_name_without_long_name(self) -> None:
        assert ok(map_profile("X", {"quoteType": "EQUITY", "shortName": "Short"})).name == "Short"

    def test_without_quote_summary_is_not_found(self) -> None:
        assert error_kind(map_profile("XXX.DE", {})) is YahooErrorKind.NOT_FOUND


class TestDividends:
    def test_london_dividends_are_converted_from_pence_and_sorted_oldest_first(self) -> None:
        dividends: Dividends = ok(map_dividends("SHEL.L", source_for("SHEL.L").dividends("SHEL.L")))

        assert dividends.currency == "GBP"
        assert dividends.dividends[-1].ex_date == date(2026, 11, 12)
        assert dividends.dividends[-1].amount == pytest.approx(0.29798874)
        assert [d.ex_date for d in dividends.dividends] == sorted(d.ex_date for d in dividends.dividends)

    def test_unsorted_history_is_sorted(self) -> None:
        history = YahooDividends(
            payments=(YahooDividend(date(2025, 5, 14), 2.35), YahooDividend(date(2024, 5, 16), 2.2)),
            currency="EUR",
        )

        assert [d.ex_date for d in ok(map_dividends("SAP.DE", history)).dividends] == [
            date(2024, 5, 16),
            date(2025, 5, 14),
        ]

    def test_empty_history_is_valid(self) -> None:
        dividends = ok(map_dividends("NODIV", YahooDividends(payments=(), currency=None)))

        assert dividends.dividends == []
        assert dividends.currency is None

    def test_dividends_without_currency_are_an_upstream_failure(self) -> None:
        history = YahooDividends(payments=(YahooDividend(date(2025, 5, 14), 2.35),), currency=None)

        assert error_kind(map_dividends("SAP.DE", history)) is YahooErrorKind.UPSTREAM_FAILURE


class TestBalanceSheet:
    def test_maps_years_newest_first_with_camel_case_keys(self) -> None:
        sheet: BalanceSheet = ok(map_balance_sheet("SAP.DE", recorded_balance_sheet("SAP.DE")))

        assert sheet.currency == "EUR"
        assert sheet.years[0].fiscal_year_end == date(2025, 12, 31)
        assert [year.fiscal_year_end for year in sheet.years] == sorted(
            (year.fiscal_year_end for year in sheet.years), reverse=True
        )
        assert "totalAssets" in sheet.years[0].items
        assert "Total Assets" not in sheet.years[0].items

    def test_missing_values_are_left_out(self) -> None:
        sheet = ok(map_balance_sheet("AAPL", recorded_balance_sheet("AAPL")))
        oldest = sheet.years[-1]

        assert oldest.fiscal_year_end == date(2022, 9, 30)
        assert "totalAssets" not in oldest.items
        assert oldest.items["otherInvestments"] == 120805000000.0

    def test_minor_unit_financial_currency_is_converted(self) -> None:
        sheet = YahooBalanceSheet(
            columns=(YahooBalanceSheetColumn(date(2025, 12, 31), {"Total Assets": 1000.0}),),
            financial_currency="GBp",
        )

        mapped = ok(map_balance_sheet("X.L", sheet))

        assert mapped.currency == "GBP"
        assert mapped.years[0].items == {"totalAssets": 10.0}

    def test_years_without_values_are_skipped(self) -> None:
        sheet = YahooBalanceSheet(
            columns=(
                YahooBalanceSheetColumn(date(2025, 12, 31), {"Total Assets": 1.0}),
                YahooBalanceSheetColumn(date(2024, 12, 31), {}),
            ),
            financial_currency="EUR",
        )

        assert [year.fiscal_year_end for year in ok(map_balance_sheet("X", sheet)).years] == [date(2025, 12, 31)]

    @pytest.mark.parametrize(
        "columns",
        [(), (YahooBalanceSheetColumn(date(2025, 12, 31), {}),)],
    )
    def test_without_values_is_not_found(self, columns: tuple[YahooBalanceSheetColumn, ...]) -> None:
        sheet = YahooBalanceSheet(columns=columns, financial_currency=None)

        assert error_kind(map_balance_sheet("X", sheet)) is YahooErrorKind.NOT_FOUND


@pytest.mark.parametrize(
    ("label", "expected"),
    [
        ("Total Assets", "totalAssets"),
        ("Net PPE", "netPPE"),
        ("Tradeand Other Payables Non Current", "tradeandOtherPayablesNonCurrent"),
        ("Goodwill", "goodwill"),
        ("", ""),
    ],
)
def test_item_key(label: str, expected: str) -> None:
    assert item_key(label) == expected
