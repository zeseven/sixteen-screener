import asyncio
import time

import pytest
from rusty_results.prelude import Err, Ok

from tests.support import FakeClock, instant_pacer
from yahoo_proxy.caller import YahooCaller
from yahoo_proxy.errors import YahooError, YahooErrorKind
from yahoo_proxy.pacer import Pacer


def classify(error: Exception) -> YahooError:
    return YahooError(YahooErrorKind.UPSTREAM_FAILURE, type(error).__name__)


def sleep_then_return(seconds: float, value: str) -> str:
    time.sleep(seconds)
    return value


def raise_key_error() -> str:
    raise KeyError("price")


def test_returns_the_operation_result() -> None:
    caller = YahooCaller(classify, timeout_seconds=1, max_concurrent_calls=1, pacer=instant_pacer())

    assert asyncio.run(caller.call(lambda: "value")) == Ok("value")


def test_classifies_exceptions() -> None:
    caller = YahooCaller(classify, timeout_seconds=1, max_concurrent_calls=1, pacer=instant_pacer())

    assert asyncio.run(caller.call(raise_key_error)) == Err(YahooError(YahooErrorKind.UPSTREAM_FAILURE, "KeyError"))


def test_slow_operation_times_out() -> None:
    caller = YahooCaller(classify, timeout_seconds=0.05, max_concurrent_calls=1, pacer=instant_pacer())

    result = asyncio.run(caller.call(lambda: sleep_then_return(0.5, "late")))

    assert result.is_err
    assert result.unwrap_err().kind is YahooErrorKind.TIMEOUT


def test_waiting_for_a_free_slot_counts_towards_the_timeout() -> None:
    caller = YahooCaller(classify, timeout_seconds=0.3, max_concurrent_calls=1, pacer=instant_pacer())

    async def two_calls() -> list[bool]:
        results = await asyncio.gather(
            caller.call(lambda: sleep_then_return(0.2, "first")),
            caller.call(lambda: sleep_then_return(0.2, "second")),
        )
        return [result.is_ok for result in results]

    assert asyncio.run(two_calls()) == [True, False]


def test_concurrent_calls_run_in_parallel_up_to_the_limit() -> None:
    caller = YahooCaller(classify, timeout_seconds=0.3, max_concurrent_calls=2, pacer=instant_pacer())

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
        YahooCaller(classify, timeout_seconds, max_concurrent_calls, instant_pacer())


async def never_wake(_seconds: float) -> None:
    await asyncio.Event().wait()


def test_reads_start_paced_even_when_several_slots_are_free() -> None:
    clock = FakeClock()
    caller = YahooCaller(classify, timeout_seconds=1, max_concurrent_calls=3, pacer=instant_pacer(clock))

    async def three_calls() -> list[float]:
        results = await asyncio.gather(*(caller.call(clock.time) for _ in range(3)))
        return [result.unwrap() for result in results]

    assert asyncio.run(three_calls()) == [1000.0, 1001.0, 1002.0]


def test_waiting_for_the_turn_counts_towards_the_timeout() -> None:
    pacer = Pacer(1.0, clock=FakeClock().time, sleep=never_wake)
    caller = YahooCaller(classify, timeout_seconds=0.05, max_concurrent_calls=2, pacer=pacer)

    async def two_calls() -> list[YahooErrorKind | None]:
        results = await asyncio.gather(caller.call(lambda: "first"), caller.call(lambda: "second"))
        return [None if result.is_ok else result.unwrap_err().kind for result in results]

    assert asyncio.run(two_calls()) == [None, YahooErrorKind.TIMEOUT]


def test_timed_out_waiter_does_not_hold_up_the_next_read() -> None:
    clock = FakeClock()
    pacer = Pacer(1.0, clock=clock.time, sleep=never_wake)
    caller = YahooCaller(classify, timeout_seconds=0.05, max_concurrent_calls=1, pacer=pacer)

    async def call_after_a_timeout() -> bool:
        await caller.call(lambda: "first")
        timed_out = await caller.call(lambda: "second")
        clock.now += 1
        late = await caller.call(lambda: "third")
        return timed_out.is_err and late == Ok("third")

    assert asyncio.run(call_after_a_timeout())
