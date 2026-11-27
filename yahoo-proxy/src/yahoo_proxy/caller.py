"""Runs blocking Yahoo calls off the event loop, with a timeout, a concurrency limit and pacing."""

import asyncio
from collections.abc import Callable

from rusty_results.prelude import Err, Ok, Result

from yahoo_proxy.errors import YahooError, YahooErrorKind
from yahoo_proxy.pacer import Pacer

type ErrorClassifier = Callable[[Exception], YahooError]


class YahooCaller:
    """Error boundary of Yahoo calls: every outcome, including exceptions and timeouts, becomes a Result."""

    def __init__(
        self,
        classify_error: ErrorClassifier,
        timeout_seconds: float,
        max_concurrent_calls: int,
        pacer: Pacer,
    ) -> None:
        if timeout_seconds <= 0 or max_concurrent_calls < 1:
            message = "Timeout must be positive and at least one concurrent call must be allowed"
            raise ValueError(message)
        self._classify_error = classify_error
        self._timeout_seconds = timeout_seconds
        self._slots = asyncio.Semaphore(max_concurrent_calls)
        self._pacer = pacer

    async def call[T](self, operation: Callable[[], T]) -> Result[T, YahooError]:
        """Runs the operation in a worker thread as one paced Yahoo read.

        Waiting for a free slot and then for the pacer's turn counts towards the timeout. The turn is taken while
        holding the slot, so reads start no faster than the pacer allows even when several slots free up at once.

        A timed out operation keeps running in its thread until the library gives up, but its result is dropped.
        """
        try:
            async with asyncio.timeout(self._timeout_seconds), self._slots:
                await self._pacer.wait_for_turn()
                return Ok(await asyncio.to_thread(operation))
        except TimeoutError:
            return Err(YahooError(YahooErrorKind.TIMEOUT, f"No answer within {self._timeout_seconds:g} s"))
        except Exception as error:
            return Err(self._classify_error(error))
