"""Entry point: `python -m yahoo_proxy`."""

import logging
import os
import sys

import uvicorn
from rusty_results.prelude import Err, Ok

from yahoo_proxy.api import create_app
from yahoo_proxy.caller import YahooCaller
from yahoo_proxy.logging_setup import configure_logging
from yahoo_proxy.pacer import Pacer
from yahoo_proxy.service import YahooService
from yahoo_proxy.settings import Settings, settings_from_env
from yahoo_proxy.yfinance_source import classify_yfinance_error, create_yfinance_source

_logger = logging.getLogger("yahoo_proxy")


def build_service(settings: Settings) -> YahooService:
    """Wires the yfinance source and the error boundary into the service."""
    caller = YahooCaller(
        classify_yfinance_error,
        settings.timeout_seconds,
        settings.max_concurrent_calls,
        Pacer(settings.min_call_interval_seconds),
    )
    return YahooService(create_yfinance_source(settings.cache_dir), caller)


def main() -> int:
    """Starts the HTTP server; returns a non-zero exit code when the configuration is invalid."""
    configure_logging()
    match settings_from_env(os.environ):
        case Err(problems):
            _logger.error("Invalid configuration: %s", problems)
            return 1
        case Ok(settings):
            uvicorn.run(
                create_app(build_service(settings)),
                host="0.0.0.0",
                port=settings.port,
                log_config=None,
                access_log=False,
            )
            return 0


if __name__ == "__main__":
    sys.exit(main())
