"""HTTP endpoints of the proxy."""

import logging
import re
from collections.abc import Awaitable, Callable, Mapping
from typing import Final

import msgspec
from fastapi import FastAPI, Response
from rusty_results.prelude import Err, Ok, Result

from yahoo_proxy.contract import ErrorBody, Health
from yahoo_proxy.errors import YahooError, YahooErrorKind
from yahoo_proxy.service import YahooService

SYMBOL_PATTERN: Final = re.compile(r"[A-Z0-9][A-Z0-9.\-]{0,24}")

STATUS_BY_ERROR_KIND: Final[Mapping[YahooErrorKind, int]] = {
    YahooErrorKind.NOT_FOUND: 404,
    YahooErrorKind.RATE_LIMITED: 429,
    YahooErrorKind.UPSTREAM_FAILURE: 502,
    YahooErrorKind.TIMEOUT: 504,
}

type Fetch[T: msgspec.Struct] = Callable[[str], Awaitable[Result[T, YahooError]]]
type Endpoint = Callable[[str], Awaitable[Response]]

_logger = logging.getLogger(__name__)


def create_app(service: YahooService) -> FastAPI:
    """Creates the application with one read-only endpoint per data type and a health endpoint."""
    app = FastAPI(title="Yahoo proxy", docs_url=None, redoc_url=None, openapi_url=None)
    app.add_api_route("/health", _health, methods=["GET"])
    app.add_api_route("/symbols/{symbol}/quote", _symbol_endpoint(service.quote), methods=["GET"])
    app.add_api_route("/symbols/{symbol}/statistics", _symbol_endpoint(service.statistics), methods=["GET"])
    app.add_api_route("/symbols/{symbol}/profile", _symbol_endpoint(service.profile), methods=["GET"])
    app.add_api_route("/symbols/{symbol}/dividends", _symbol_endpoint(service.dividends), methods=["GET"])
    app.add_api_route("/symbols/{symbol}/balance-sheet", _symbol_endpoint(service.balance_sheet), methods=["GET"])
    return app


async def _health() -> Response:
    return _json(Health(status="ok"))


def _symbol_endpoint[T: msgspec.Struct](fetch: Fetch[T]) -> Endpoint:
    async def endpoint(symbol: str) -> Response:
        return await _respond(symbol, fetch)

    return endpoint


async def _respond[T: msgspec.Struct](symbol: str, fetch: Fetch[T]) -> Response:
    if SYMBOL_PATTERN.fullmatch(symbol) is None:
        return _json(ErrorBody(error="INVALID_SYMBOL", message=f"Not a Yahoo symbol: {symbol!r}"), status_code=400)
    match await fetch(symbol):
        case Ok(body):
            return _json(body)
        case Err(error):
            _logger.warning("Yahoo call for %s failed with %s: %s", symbol, error.kind, error.message)
            body = ErrorBody(error=error.kind.value, message=error.message)
            return _json(body, status_code=STATUS_BY_ERROR_KIND[error.kind])


def _json(body: msgspec.Struct, status_code: int = 200) -> Response:
    return Response(content=msgspec.json.encode(body), status_code=status_code, media_type="application/json")
