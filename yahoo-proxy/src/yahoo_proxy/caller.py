"""Runs blocking Yahoo calls off the event loop, with a timeout and a concurrency limit."""

import asyncio
from collections.abc import Callable

from rusty_results.prelude import Err, Ok, Result

from yahoo_proxy.errors import YahooError, YahooErrorKind

type ErrorClassifier = Callable[[Exception], YahooError]


class YahooCaller:
    """Error boundary of Yahoo calls: every outcome, including exceptions and timeouts, becomes a Result."""

    def __init__(self, classify_error: ErrorClassifier, timeout_seconds: float, max_concurrent_calls: int) -> None:
        if timeout_seconds <= 0 or max_concurrent_calls < 1:
            message = "Timeout must be positive and at least one concurrent call must be allowed"
            raise ValueError(message)
        self._classify_error = classify_error
        self._timeout_seconds = timeout_seconds
        self._slots = asyncio.Semaphore(max_concurrent_calls)

    async def call[T](self, operation: Callable[[], T]) -> Result[T, YahooError]:
        """Runs the operation in a worker thread; waiting for a free slot counts towards the timeout.

        A timed out operation keeps running in its thread until the library gives up, but its result is dropped.
        """
        try:
            async with asyncio.timeout(self._timeout_seconds), self._slots:
                return Ok(await asyncio.to_thread(operation))
        except TimeoutError:
            return Err(YahooError(YahooErrorKind.TIMEOUT, f"No answer within {self._timeout_seconds:g} s"))
        except Exception as error:
            return Err(self._classify_error(error))
