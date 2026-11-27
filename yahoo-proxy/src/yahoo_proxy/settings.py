"""Configuration from environment variables."""

import math
import tempfile
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Final

from rusty_results.prelude import Err, Ok, Result

DEFAULT_PORT: Final = 8080
DEFAULT_TIMEOUT_SECONDS: Final = 25.0
DEFAULT_MAX_CONCURRENT_CALLS: Final = 2
DEFAULT_MIN_CALL_INTERVAL_SECONDS: Final = 1.0


@dataclass(frozen=True, slots=True)
class Settings:
    """Validated configuration of the proxy."""

    port: int
    timeout_seconds: float
    max_concurrent_calls: int
    min_call_interval_seconds: float
    cache_dir: Path


def settings_from_env(env: Mapping[str, str]) -> Result[Settings, str]:
    """Reads the settings, reporting every invalid variable at once."""
    port = _parse(env, "PORT", int, DEFAULT_PORT, lambda value: 0 < value < 65536)
    timeout = _parse(env, "YAHOO_TIMEOUT_SECONDS", float, DEFAULT_TIMEOUT_SECONDS, lambda value: value > 0)
    concurrency = _parse(env, "YAHOO_MAX_CONCURRENT_CALLS", int, DEFAULT_MAX_CONCURRENT_CALLS, lambda value: value > 0)
    interval = _parse(
        env, "YAHOO_MIN_CALL_INTERVAL_SECONDS", float, DEFAULT_MIN_CALL_INTERVAL_SECONDS, _is_positive_finite
    )
    cache_dir = Path(env.get("YFINANCE_CACHE_DIR") or Path(tempfile.gettempdir()) / "yfinance")
    match port, timeout, concurrency, interval:
        case Ok(port_value), Ok(timeout_value), Ok(concurrency_value), Ok(interval_value):
            return Ok(Settings(port_value, timeout_value, concurrency_value, interval_value, cache_dir))
        case _:
            problems = [result.unwrap_err() for result in (port, timeout, concurrency, interval) if result.is_err]
            return Err("; ".join(problems))


def _parse[T: (int, float)](
    env: Mapping[str, str],
    name: str,
    convert: Callable[[str], T],
    default: T,
    is_valid: Callable[[T], bool],
) -> Result[T, str]:
    raw = env.get(name)
    if raw is None or not raw.strip():
        return Ok(default)
    try:
        value = convert(raw.strip())
    except ValueError:
        return Err(f"{name} is not a number: {raw!r}")
    return Ok(value) if is_valid(value) else Err(f"{name} is out of range: {raw!r}")


def _is_positive_finite(value: float) -> bool:
    return math.isfinite(value) and value > 0
