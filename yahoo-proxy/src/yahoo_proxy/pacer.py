"""Spacing of Yahoo reads, so that the proxy never starts them faster than a configured rate."""

import asyncio
import math
import time
from collections.abc import Awaitable, Callable

type Clock = Callable[[], float]
type Sleep = Callable[[float], Awaitable[None]]


class Pacer:
    """Grants turns in arrival order, at least `min_interval_seconds` apart.

    A waiter cancelled before its turn (e.g. by a timeout) gives its turn to the next one.
    """

    def __init__(
        self,
        min_interval_seconds: float,
        clock: Clock = time.monotonic,
        sleep: Sleep = asyncio.sleep,
    ) -> None:
        if not min_interval_seconds > 0:
            message = "Minimum interval between Yahoo reads must be positive"
            raise ValueError(message)
        self._min_interval_seconds = min_interval_seconds
        self._clock = clock
        self._sleep = sleep
        self._turns = asyncio.Lock()
        self._next_start = -math.inf

    async def wait_for_turn(self) -> None:
        """Returns when the caller may start its read; the start is recorded on return."""
        async with self._turns:
            delay = self._next_start - self._clock()
            if delay > 0:
                await self._sleep(delay)
            self._next_start = self._clock() + self._min_interval_seconds
