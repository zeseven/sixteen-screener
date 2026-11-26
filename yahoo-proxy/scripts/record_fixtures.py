"""Records real yfinance results of the given symbols as test fixtures in `tests/fixtures/{symbol}.json`.

Usage: `uv run python scripts/record_fixtures.py AAPL SAP.DE SHEL.L`
"""

import json
import math
import sys
from collections.abc import Hashable
from datetime import datetime
from pathlib import Path
from typing import Final

import pandas as pd
import yfinance

FIXTURES_DIR: Final = Path(__file__).resolve().parent.parent / "tests" / "fixtures"
PERSONAL_INFO_KEYS: Final = frozenset({"companyOfficers", "executiveTeam"})
PRICE_METADATA_KEYS: Final = (
    "currency",
    "regularMarketPrice",
    "chartPreviousClose",
    "regularMarketTime",
    "fullExchangeName",
    "exchangeName",
)


def record(symbol: str) -> dict[str, object]:
    """Reads every data type of a symbol the way the proxy does and returns it as JSON-ready data."""
    yfinance.config.debug.hide_exceptions = False
    info = yfinance.Ticker(symbol).info
    price_ticker = yfinance.Ticker(symbol)
    price_ticker.history(period="5d", interval="1d", auto_adjust=False)
    metadata = price_ticker.get_history_metadata()
    dividend_ticker = yfinance.Ticker(symbol)
    dividends = dividend_ticker.get_dividends(period="max")
    balance_sheet = yfinance.Ticker(symbol).balance_sheet
    return {
        "symbol": symbol,
        "yfinanceVersion": yfinance.__version__,
        "info": {key: value for key, value in info.items() if key not in PERSONAL_INFO_KEYS},
        "priceMetadata": {key: _json_value(metadata.get(key)) for key in PRICE_METADATA_KEYS},
        "dividendCurrency": dividend_ticker.get_history_metadata().get("currency"),
        "dividends": [{"exDate": _iso(label), "amount": float(amount)} for label, amount in dividends.items()],
        "balanceSheet": {_iso(label).split("T")[0]: _column_values(column) for label, column in balance_sheet.items()},
    }


def _json_value(value: object) -> object:
    return value.isoformat() if isinstance(value, datetime) else value


def _iso(label: Hashable) -> str:
    if not isinstance(label, datetime):
        message = f"Expected a date label, got {label!r}"
        raise TypeError(message)
    return label.isoformat()


def _column_values(column: pd.Series[float]) -> dict[str, float | None]:
    return {str(item): _finite_or_none(value) for item, value in column.items()}


def _finite_or_none(value: object) -> float | None:
    if isinstance(value, int | float) and not isinstance(value, bool) and math.isfinite(value):
        return float(value)
    return None


def main(symbols: list[str]) -> int:
    """Records each symbol; returns a non-zero exit code when no symbol is given."""
    if not symbols:
        print(__doc__, file=sys.stderr)
        return 2
    FIXTURES_DIR.mkdir(parents=True, exist_ok=True)
    for symbol in symbols:
        path = FIXTURES_DIR / f"{symbol}.json"
        path.write_text(json.dumps(record(symbol), indent=2, sort_keys=True, ensure_ascii=False) + "\n")
        print(f"Recorded {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
