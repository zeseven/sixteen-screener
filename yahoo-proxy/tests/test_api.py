import time
from collections.abc import Callable

import msgspec
import pytest
from fastapi.testclient import TestClient
from yfinance.exceptions import YFRateLimitError

from tests.support import RecordedTicker, failing_tickers, http_error, load_fixture, recorded_tickers
from yahoo_proxy.api import create_app
from yahoo_proxy.caller import YahooCaller
from yahoo_proxy.contract import BalanceSheet, Dividends, ErrorBody, Health, Profile, Quote, Statistics
from yahoo_proxy.service import YahooService
from yahoo_proxy.yfinance_source import TickerLike, YfinanceSource, classify_yfinance_error


def client_for(ticker_factory: Callable[[str], TickerLike], timeout_seconds: float = 5) -> TestClient:
    caller = YahooCaller(classify_yfinance_error, timeout_seconds=timeout_seconds, max_concurrent_calls=2)
    return TestClient(create_app(YahooService(YfinanceSource(ticker_factory), caller)))


def decode[T](content: bytes, body_type: type[T]) -> T:
    return msgspec.json.decode(content, type=body_type)


def test_health_answers_without_yahoo() -> None:
    response = client_for(failing_tickers(AssertionError("Yahoo must not be called"))).get("/health")

    assert response.status_code == 200
    assert decode(response.content, Health) == Health(status="ok")


def test_quote_in_main_currency_unit_with_camel_case_fields() -> None:
    response = client_for(recorded_tickers()).get("/symbols/SHEL.L/quote")

    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    body = decode(response.content, Quote)
    assert body.symbol == "SHEL.L"
    assert body.currency == "GBP"
    assert body.price == pytest.approx(36.16)
    assert '"previousClose":' in response.text
    assert '"marketTime":"2026-11-26T14:30:13Z"' in response.text


def test_statistics() -> None:
    response = client_for(recorded_tickers()).get("/symbols/AAPL/statistics")

    assert response.status_code == 200
    body = decode(response.content, Statistics)
    assert body.currency == "USD"
    assert body.ratios["trailingPE"] > 0


def test_profile() -> None:
    response = client_for(recorded_tickers()).get("/symbols/SAP.DE/profile")

    assert response.status_code == 200
    assert decode(response.content, Profile).name == "SAP SE"
    assert '"financialCurrency":"EUR"' in response.text


def test_dividends() -> None:
    response = client_for(recorded_tickers()).get("/symbols/SAP.DE/dividends")

    assert response.status_code == 200
    body = decode(response.content, Dividends)
    assert body.currency == "EUR"
    assert body.dividends[-1].amount == 2.5
    assert '"exDate":"2026-05-06"' in response.text


def test_balance_sheet() -> None:
    response = client_for(recorded_tickers()).get("/symbols/AAPL/balance-sheet")

    assert response.status_code == 200
    body = decode(response.content, BalanceSheet)
    assert body.currency == "USD"
    assert "totalAssets" in body.years[0].items
    assert '"fiscalYearEnd":"2026-09-30"' in response.text


@pytest.mark.parametrize(
    ("error", "status_code", "error_kind"),
    [
        (http_error(404), 404, "NOT_FOUND"),
        (YFRateLimitError(), 429, "RATE_LIMITED"),
        (http_error(429), 429, "RATE_LIMITED"),
        (http_error(500), 502, "UPSTREAM_FAILURE"),
        (KeyError("unexpected shape"), 502, "UPSTREAM_FAILURE"),
    ],
)
@pytest.mark.parametrize("endpoint", ["quote", "statistics", "profile", "dividends", "balance-sheet"])
def test_yahoo_failures_map_to_status_codes(endpoint: str, error: Exception, status_code: int, error_kind: str) -> None:
    response = client_for(failing_tickers(error)).get(f"/symbols/SAP.DE/{endpoint}")

    assert response.status_code == status_code
    assert decode(response.content, ErrorBody).error == error_kind


def test_info_without_quote_summary_is_not_found() -> None:
    class UnknownTicker(RecordedTicker):
        @property
        def info(self) -> dict[str, object]:
            return {"trailingPegRatio": None}

    response = client_for(lambda _symbol: UnknownTicker(load_fixture("SAP.DE"))).get("/symbols/XXX.DE/profile")

    assert response.status_code == 404
    assert decode(response.content, ErrorBody).error == "NOT_FOUND"


def test_slow_yahoo_answers_with_gateway_timeout() -> None:
    class SlowTicker(RecordedTicker):
        @property
        def info(self) -> dict[str, object]:
            time.sleep(0.5)
            return {}

    client = client_for(lambda _symbol: SlowTicker(load_fixture("SAP.DE")), timeout_seconds=0.05)

    response = client.get("/symbols/SAP.DE/profile")

    assert response.status_code == 504
    assert decode(response.content, ErrorBody).error == "TIMEOUT"


@pytest.mark.parametrize("symbol", ["sap.de", ".DE", "SAP DE", "SAP%2FDE", "A" * 26, "^GSPC"])
def test_invalid_symbols_are_rejected_without_yahoo_call(symbol: str) -> None:
    response = client_for(failing_tickers(AssertionError("Yahoo must not be called"))).get(f"/symbols/{symbol}/quote")

    assert response.status_code in {400, 404}
    if response.status_code == 400:
        assert decode(response.content, ErrorBody).error == "INVALID_SYMBOL"


@pytest.mark.parametrize("symbol", ["SAP.DE", "BRK-B", "7203.T", "A"])
def test_valid_symbols_are_accepted(symbol: str) -> None:
    response = client_for(failing_tickers(http_error(404))).get(f"/symbols/{symbol}/quote")

    assert response.status_code == 404
    assert decode(response.content, ErrorBody).error == "NOT_FOUND"


def test_unknown_paths_are_not_found() -> None:
    assert client_for(recorded_tickers()).get("/symbols/SAP.DE/unknown").status_code == 404
