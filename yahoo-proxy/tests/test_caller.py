import asyncio
import time

import pytest
from rusty_results.prelude import Err, Ok

from yahoo_proxy.caller import YahooCaller
from yahoo_proxy.errors import YahooError, YahooErrorKind


def classify(error: Exception) -> YahooError:
    return YahooError(YahooErrorKind.UPSTREAM_FAILURE, type(error).__name__)


def sleep_then_return(seconds: float, value: str) -> str:
    time.sleep(seconds)
    return value


def raise_key_error() -> str:
    raise KeyError("price")


def test_returns_the_operation_result() -> None:
    caller = YahooCaller(classify, timeout_seconds=1, max_concurrent_calls=1)

    assert asyncio.run(caller.call(lambda: "value")) == Ok("value")


def test_classifies_exceptions() -> None:
    caller = YahooCaller(classify, timeout_seconds=1, max_concurrent_calls=1)

    assert asyncio.run(caller.call(raise_key_error)) == Err(YahooError(YahooErrorKind.UPSTREAM_FAILURE, "KeyError"))


def test_slow_operation_times_out() -> None:
    caller = YahooCaller(classify, timeout_seconds=0.05, max_concurrent_calls=1)

    result = asyncio.run(caller.call(lambda: sleep_then_return(0.5, "late")))

    assert result.is_err
    assert result.unwrap_err().kind is YahooErrorKind.TIMEOUT


def test_waiting_for_a_free_slot_counts_towards_the_timeout() -> None:
    caller = YahooCaller(classify, timeout_seconds=0.3, max_concurrent_calls=1)

    async def two_calls() -> list[bool]:
        results = await asyncio.gather(
            caller.call(lambda: sleep_then_return(0.2, "first")),
            caller.call(lambda: sleep_then_return(0.2, "second")),
        )
        return [result.is_ok for result in results]

    assert asyncio.run(two_calls()) == [True, False]


def test_concurrent_calls_run_in_parallel_up_to_the_limit() -> None:
    caller = YahooCaller(classify, timeout_seconds=0.3, max_concurrent_calls=2)

    async def two_calls() -> list[bool]:
        results = await asyncio.gather(
            caller.call(lambda: sleep_then_return(0.2, "first")),
            caller.call(lambda: sleep_then_return(0.2, "second")),
        )
        return [result.is_ok for result in results]

    assert asyncio.run(two_calls()) == [True, True]


@pytest.mark.parametrize(("timeout_seconds", "max_concurrent_calls"), [(0, 1), (-1, 1), (1, 0)])
def test_rejects_invalid_limits(timeout_seconds: float, max_concurrent_calls: int) -> None:
    with pytest.raises(ValueError, match="Timeout must be positive"):
        YahooCaller(classify, timeout_seconds, max_concurrent_calls)
