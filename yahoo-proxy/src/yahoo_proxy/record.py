"""Typed reading of loosely typed Yahoo records.

A field of an unexpected type is treated as missing, so one odd value never fails a whole response.
"""

import math
from collections.abc import Mapping
from datetime import UTC, datetime

type YahooRecord = Mapping[str, object]


def number(record: YahooRecord, key: str) -> float | None:
    """Returns a finite number field, or None."""
    value = record.get(key)
    if isinstance(value, bool) or not isinstance(value, int | float):
        return None
    return float(value) if math.isfinite(value) else None


def integer(record: YahooRecord, key: str) -> int | None:
    """Returns an integer field (an integral float is accepted), or None."""
    value = number(record, key)
    return int(value) if value is not None and value.is_integer() else None


def text(record: YahooRecord, key: str) -> str | None:
    """Returns a non-blank text field without surrounding whitespace, or None."""
    value = record.get(key)
    if not isinstance(value, str):
        return None
    stripped = value.strip()
    return stripped or None


def timestamp(record: YahooRecord, key: str) -> datetime | None:
    """Returns a time field given as an aware datetime or epoch seconds, in UTC, or None."""
    value = record.get(key)
    if isinstance(value, datetime):
        return datetime.fromtimestamp(value.timestamp(), UTC) if value.tzinfo is not None else None
    seconds = integer(record, key)
    return datetime.fromtimestamp(seconds, UTC) if seconds is not None else None


def first_text(record: YahooRecord, *keys: str) -> str | None:
    """Returns the first non-blank text field among the keys, or None."""
    return next((value for key in keys if (value := text(record, key)) is not None), None)
