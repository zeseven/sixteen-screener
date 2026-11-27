import asyncio
import math

import pytest

from tests.support import FakeClock, instant_pacer
from yahoo_proxy.pacer import Pacer


async def starts_of(pacer: Pacer, clock: FakeClock, count: int) -> list[float]:
    starts: list[float] = []

    async def read() -> None:
        await pacer.wait_for_turn()
        starts.append(clock.now)

    await asyncio.gather(*(read() for _ in range(count)))
    return starts


def test_first_read_starts_without_waiting() -> None:
    clock = FakeClock()

    asyncio.run(instant_pacer(clock).wait_for_turn())

    assert clock.sleeps == []


def test_consecutive_reads_start_one_interval_apart() -> None:
    clock = FakeClock()

    starts = asyncio.run(starts_of(instant_pacer(clock, min_interval_seconds=0.5), clock, 4))

    assert starts == [1000.0, 1000.5, 1001.0, 1001.5]


def test_reads_start_in_arrival_order() -> None:
    clock = FakeClock()
    pacer = instant_pacer(clock)
    order: list[int] = []

    async def read(number: int) -> None:
        await pacer.wait_for_turn()
        order.append(number)

    async def arrivals() -> None:
        await asyncio.gather(*(read(number) for number in range(5)))

    asyncio.run(arrivals())

    assert order == [0, 1, 2, 3, 4]


def test_no_wait_after_a_long_idle_gap() -> None:
    clock = FakeClock()
    pacer = instant_pacer(clock)

    async def two_reads_with_gap() -> None:
        await pacer.wait_for_turn()
        clock.now += 60
        await pacer.wait_for_turn()

    asyncio.run(two_reads_with_gap())

    assert clock.sleeps == []


def test_waits_only_for_the_rest_of_the_interval() -> None:
    clock = FakeClock()
    pacer = instant_pacer(clock)

    async def two_reads_with_short_gap() -> None:
        await pacer.wait_for_turn()
        clock.now += 0.25
        await pacer.wait_for_turn()

    asyncio.run(two_reads_with_short_gap())

    assert clock.sleeps == [0.75]


def test_waiting_does_not_block_other_tasks() -> None:
    pacer = Pacer(0.05)
    events: list[str] = []

    async def read(name: str) -> None:
        await pacer.wait_for_turn()
        events.append(name)

    async def other_work() -> None:
        await asyncio.sleep(0.01)
        events.append("other")

    async def run() -> None:
        await asyncio.gather(read("first"), read("second"), other_work())

    asyncio.run(run())

    assert events == ["first", "other", "second"]


@pytest.mark.parametrize("interval", [0, -1, math.nan])
def test_rejects_non_positive_interval(interval: float) -> None:
    with pytest.raises(ValueError, match="must be positive"):
        Pacer(interval)
