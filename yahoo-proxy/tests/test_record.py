from datetime import UTC, datetime, timedelta, timezone

import pandas as pd
import pytest

from yahoo_proxy.record import first_text, integer, number, text, timestamp


@pytest.mark.parametrize(("value", "expected"), [(1.5, 1.5), (3, 3.0), (0, 0.0), (-2.25, -2.25)])
def test_number_accepts_finite_numbers(value: object, expected: float) -> None:
    assert number({"key": value}, "key") == expected


@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf"), True, "1.5", None, [1.0]])
def test_number_rejects_non_finite_and_non_numeric_values(value: object) -> None:
    assert number({"key": value}, "key") is None


def test_number_of_missing_key_is_none() -> None:
    assert number({}, "key") is None


@pytest.mark.parametrize(("value", "expected"), [(84000, 84000), (84000.0, 84000), (84000.5, None), ("84000", None)])
def test_integer_accepts_integral_numbers_only(value: object, expected: int | None) -> None:
    assert integer({"key": value}, "key") == expected


@pytest.mark.parametrize(("value", "expected"), [("  Shell plc ", "Shell plc"), ("   ", None), ("", None), (5, None)])
def test_text_strips_and_rejects_blank_or_non_text_values(value: object, expected: str | None) -> None:
    assert text({"key": value}, "key") == expected


def test_timestamp_converts_aware_datetimes_to_utc() -> None:
    local = datetime(2026, 11, 26, 15, 25, tzinfo=timezone(timedelta(hours=1)))
    assert timestamp({"key": local}, "key") == datetime(2026, 11, 26, 14, 25, tzinfo=UTC)


def test_timestamp_accepts_pandas_timestamps() -> None:
    assert timestamp({"key": pd.Timestamp("2026-11-25T16:00:02-05:00")}, "key") == datetime(
        2026, 11, 25, 21, 0, 2, tzinfo=UTC
    )


def test_timestamp_of_pandas_timestamp_is_a_plain_datetime() -> None:
    value = timestamp({"key": pd.Timestamp("2026-11-25T16:00:02-05:00")}, "key")

    assert type(value) is datetime


def test_timestamp_accepts_epoch_seconds() -> None:
    assert timestamp({"key": 1_795_000_000}, "key") == datetime.fromtimestamp(1_795_000_000, UTC)


@pytest.mark.parametrize("value", [datetime(2026, 11, 26, 15, 25), "2026-11-26", None, 1.5])
def test_timestamp_rejects_naive_datetimes_and_other_values(value: object) -> None:
    assert timestamp({"key": value}, "key") is None


def test_first_text_returns_first_non_blank_value() -> None:
    assert first_text({"longName": " ", "shortName": "SAP SE"}, "longName", "shortName") == "SAP SE"


def test_first_text_without_any_value_is_none() -> None:
    assert first_text({"longName": None}, "longName", "shortName") is None
