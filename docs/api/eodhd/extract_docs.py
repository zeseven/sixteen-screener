# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Download the EODHD API documentation into local files.

EODHD publishes its documentation in machine-readable form: an OpenAPI 3.1 specification (bundled into one file on
GitHub Pages), ``llms.txt`` (overview) and ``llms-full.txt`` (complete reference with call costs and edge cases).
This tool downloads those files unchanged, converts the plan comparison table of the pricing page into
``pricing.md`` and stores responses of the public ``demo`` key (AAPL.US and a few other symbols) in ``examples/``.

Usage: ``uv run docs/api/eodhd/extract_docs.py [--out DIR] [--skip-examples]``
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Final

OPENAPI_URL: Final = "https://eodhistoricaldata.github.io/EODHD-openapi/openapi.yaml"
LLMS_URL: Final = "https://eodhd.com/llms.txt"
LLMS_FULL_URL: Final = "https://eodhd.com/llms-full.txt"
PRICING_URL: Final = "https://eodhd.com/pricing"
API_BASE: Final = "https://eodhd.com/api/"
USER_AGENT: Final = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
)
PLAN_COLUMNS: Final = ("Free", "EOD Historical", "EOD + Intraday", "Fundamentals", "All-In-One")
MAX_FEATURE_DETAIL_LENGTH: Final = 80
DEMO_REQUEST_DELAY_SECONDS: Final = 1.0
SAMPLE_LIST_ITEMS: Final = 3
SAMPLE_SERIES_ENTRIES: Final = 2
DATE_KEY: Final = re.compile(r"\d{4}-\d{2}-\d{2}")

type Json = None | bool | int | float | str | list[Json] | dict[str, Json]


@dataclass(frozen=True, slots=True)
class DemoRequest:
    """A request made with the public demo key and the example file it is stored in."""

    name: str
    path: str


DEMO_REQUESTS: Final = (
    DemoRequest("eod", "eod/AAPL.US?from=2026-01-02&to=2026-01-08&fmt=json"),
    DemoRequest("real-time-batch", "real-time/AAPL.US?s=MSFT.US,VTI.US&fmt=json"),
    DemoRequest("us-quote-delayed", "us-quote-delayed?s=AAPL.US,MSFT.US&fmt=json"),
    DemoRequest("div", "div/AAPL.US?from=2025-01-01&fmt=json"),
    DemoRequest("splits", "splits/AAPL.US?fmt=json"),
    DemoRequest("calendar-dividends", "calendar/dividends?filter[symbol]=AAPL.US&fmt=json"),
    DemoRequest(
        "fundamentals-profile-and-ratios",
        "fundamentals/AAPL.US?filter=General,Highlights,Valuation,SharesStats,Technicals,SplitsDividends&fmt=json",
    ),
    DemoRequest(
        "fundamentals-balance-sheet-yearly", "fundamentals/AAPL.US?filter=Financials::Balance_Sheet::yearly&fmt=json"
    ),
    DemoRequest(
        "fundamentals-etf", "fundamentals/VTI.US?filter=General,ETF_Data::Expense_Ratio,ETF_Data::Yield&fmt=json"
    ),
)


@dataclass(frozen=True, slots=True)
class FetchError:
    """A resource that could not be downloaded."""

    url: str
    reason: str


def fetch(url: str) -> str | FetchError:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,*/*"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:  # pyright: ignore[reportAny]  # rationale: urlopen is untyped, body checked below
            body: object = response.read()  # pyright: ignore[reportAny]  # rationale: same untyped response
    except (urllib.error.URLError, TimeoutError) as error:
        return FetchError(url, str(error))
    if not isinstance(body, bytes):
        return FetchError(url, "unexpected response body")
    try:
        return body.decode("utf-8")
    except UnicodeDecodeError as error:
        return FetchError(url, str(error))


def decode_json(text: str) -> Json:
    """Decode JSON text; raises ``json.JSONDecodeError`` like ``json.loads``."""
    value: Json = json.loads(text)  # pyright: ignore[reportAny]  # rationale: json.loads is untyped, JSON maps to Json
    return value


def openapi_problem(spec: str) -> str | None:
    """Return why a downloaded spec is unusable, or None when it is a bundled OpenAPI 3.1 document."""
    if not spec.startswith("openapi: 3.1"):
        return "not an OpenAPI 3.1 document"
    external = re.findall(r"\$ref:\s*['\"]?(?!#)([^'\"\s]+)", spec)
    return f"external references: {', '.join(sorted(set(external))[:5])}" if external else None


def cell_text(fragment: str) -> str:
    text = html.unescape(re.sub(r"<[^>]+>", " ", fragment))
    return re.sub(r"\s+", " ", text).strip().replace("|", "/")


def pricing_rows(page: str) -> list[tuple[str, list[str]]]:
    """Read the plan comparison table: one ``(feature, values per plan)`` tuple per row with plan values."""
    rows: list[tuple[str, list[str]]] = []
    table_rows: list[str] = re.findall(r"<tr.*?</tr>", page, flags=re.DOTALL)
    for row in table_rows:
        label = re.search(r'<span class="pricing-matrix__feature">(.*?)</span>(.*?)</th>', row, flags=re.DOTALL)
        cells: list[str] = re.findall(r"<td.*?</td>", row, flags=re.DOTALL)
        if label is None or len(cells) != len(PLAN_COLUMNS):
            continue
        feature = cell_text(label.group(1))
        detail = cell_text(label.group(2))
        has_short_detail = detail and len(detail) <= MAX_FEATURE_DETAIL_LENGTH
        rows.append((f"{feature} ({detail})" if has_short_detail else feature, [cell_value(cell) for cell in cells]))
    return rows


def cell_value(cell: str) -> str:
    text = cell_text(cell).replace("–", "").strip()
    if text:
        return text
    if "pricing-matrix__value--yes" in cell:
        return "yes"
    if "pricing-matrix__value--no" in cell:
        return "no"
    return ""


def pricing_markdown(rows: Sequence[tuple[str, list[str]]]) -> str:
    lines = [
        "# EODHD plans (personal use)",
        "",
        f"Plan comparison table copied from {PRICING_URL}. Prices: Free EUR 0, EOD Historical EUR 19.99/month,",
        "EOD + Intraday EUR 29.99/month, Fundamentals EUR 59.99/month, All-In-One EUR 99.99/month",
        "(about two months off on annual billing). See `llms-full.txt` (Subscription Plans) for USD prices.",
        "",
        "| Feature | " + " | ".join(PLAN_COLUMNS) + " |",
        "|---|" + "---|" * len(PLAN_COLUMNS),
    ]
    lines += [f"| {feature} | " + " | ".join(values) + " |" for feature, values in rows]
    return "\n".join(lines) + "\n"


def is_date_series(value: Json) -> bool:
    return isinstance(value, dict) and bool(value) and all(DATE_KEY.match(key) for key in value)


def trimmed(value: Json) -> Json:
    """Shorten a response for the docs: keep the first items of lists and the first entries of date series."""
    if isinstance(value, list):
        return [trimmed(item) for item in value[:SAMPLE_LIST_ITEMS]]
    if is_date_series(value) and isinstance(value, dict):
        return {key: trimmed(item) for key, item in list(value.items())[:SAMPLE_SERIES_ENTRIES]}
    if isinstance(value, dict):
        return {key: trimmed(item) for key, item in value.items()}
    return value


def demo_url(request: DemoRequest) -> str:
    separator = "&" if "?" in request.path else "?"
    return f"{API_BASE}{request.path}{separator}api_token=demo"


def download_examples(out: Path) -> list[str] | FetchError:
    folder = out / "examples"
    folder.mkdir(parents=True, exist_ok=True)
    written: list[str] = []
    for request in DEMO_REQUESTS:
        content = fetch(demo_url(request))
        if isinstance(content, FetchError):
            return content
        try:
            sample = trimmed(decode_json(content))
        except json.JSONDecodeError:
            return FetchError(demo_url(request), "response is not JSON")
        _ = (folder / f"{request.name}.json").write_text(
            json.dumps(sample, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        written.append(request.name)
        time.sleep(DEMO_REQUEST_DELAY_SECONDS)
    return written


def download_documents(out: Path) -> FetchError | None:
    documents: dict[str, str] = {}
    for name, url in (("openapi.yaml", OPENAPI_URL), ("llms.txt", LLMS_URL), ("llms-full.txt", LLMS_FULL_URL)):
        content = fetch(url)
        if isinstance(content, FetchError):
            return content
        documents[name] = content
    if problem := openapi_problem(documents["openapi.yaml"]):
        return FetchError(OPENAPI_URL, problem)
    pricing = fetch(PRICING_URL)
    if isinstance(pricing, FetchError):
        return pricing
    rows = pricing_rows(pricing)
    if not rows:
        return FetchError(PRICING_URL, "plan comparison table not found")
    for name, content in documents.items():
        _ = (out / name).write_text(content, encoding="utf-8")
    _ = (out / "pricing.md").write_text(pricing_markdown(rows), encoding="utf-8")
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    _ = parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent, help="output folder")
    _ = parser.add_argument("--skip-examples", action="store_true", help="keep the existing demo responses")
    arguments = parser.parse_args()
    out = Path(str(arguments.out))  # pyright: ignore[reportAny]  # rationale: argparse Namespace is untyped
    skip_examples = bool(arguments.skip_examples)  # pyright: ignore[reportAny]  # rationale: same
    out.mkdir(parents=True, exist_ok=True)
    if error := download_documents(out):
        print(f"Error: cannot download {error.url}: {error.reason}", file=sys.stderr)
        return 1
    examples = [] if skip_examples else download_examples(out)
    if isinstance(examples, FetchError):
        print(f"Error: cannot download {examples.url}: {examples.reason}", file=sys.stderr)
        return 1
    print(f"openapi.yaml, llms.txt, llms-full.txt, pricing.md and {len(examples)} demo responses -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
