import tempfile
from pathlib import Path

import pytest
from rusty_results.prelude import Err, Ok

from yahoo_proxy.settings import Settings, settings_from_env


def test_defaults_without_variables() -> None:
    assert settings_from_env({}) == Ok(Settings(8080, 25.0, 2, 1.0, Path(tempfile.gettempdir()) / "yfinance"))


def test_blank_variables_use_defaults() -> None:
    assert settings_from_env({"PORT": " ", "YAHOO_TIMEOUT_SECONDS": "", "YFINANCE_CACHE_DIR": ""}) == settings_from_env(
        {}
    )


def test_reads_all_variables() -> None:
    env = {
        "PORT": "9000",
        "YAHOO_TIMEOUT_SECONDS": "12.5",
        "YAHOO_MAX_CONCURRENT_CALLS": "4",
        "YAHOO_MIN_CALL_INTERVAL_SECONDS": "0.5",
        "YFINANCE_CACHE_DIR": "/tmp/yf",
    }

    assert settings_from_env(env) == Ok(Settings(9000, 12.5, 4, 0.5, Path("/tmp/yf")))


def test_reports_every_invalid_variable() -> None:
    env = {
        "PORT": "http",
        "YAHOO_TIMEOUT_SECONDS": "0",
        "YAHOO_MAX_CONCURRENT_CALLS": "-1",
        "YAHOO_MIN_CALL_INTERVAL_SECONDS": "never",
    }

    assert settings_from_env(env) == Err(
        "PORT is not a number: 'http'; "
        "YAHOO_TIMEOUT_SECONDS is out of range: '0'; "
        "YAHOO_MAX_CONCURRENT_CALLS is out of range: '-1'; "
        "YAHOO_MIN_CALL_INTERVAL_SECONDS is not a number: 'never'"
    )


def test_rejects_port_out_of_range() -> None:
    assert settings_from_env({"PORT": "70000"}) == Err("PORT is out of range: '70000'")


def test_rejects_fractional_integer_values() -> None:
    assert settings_from_env({"YAHOO_MAX_CONCURRENT_CALLS": "1.5"}) == Err(
        "YAHOO_MAX_CONCURRENT_CALLS is not a number: '1.5'"
    )


@pytest.mark.parametrize("interval", ["0", "-1", "nan", "inf"])
def test_rejects_min_call_interval_that_is_not_positive_and_finite(interval: str) -> None:
    assert settings_from_env({"YAHOO_MIN_CALL_INTERVAL_SECONDS": interval}) == Err(
        f"YAHOO_MIN_CALL_INTERVAL_SECONDS is out of range: {interval!r}"
    )
