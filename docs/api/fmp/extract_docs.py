# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Extract the FMP stable API documentation into local Markdown and JSON files.

The FMP docs site is a Next.js application. Endpoint metadata, documented example responses and the
per-plan coverage tables are embedded in its JavaScript bundles as ``JSON.parse('...')`` literals, and the
plan matrix is rendered into the pricing page. This tool downloads those resources and regenerates
``README``-adjacent files in this folder: ``endpoints.md``, ``examples/``, ``plans.md`` and ``catalog.json``.

Usage: ``uv run docs/api/fmp/extract_docs.py [--out DIR]``
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Iterable, Iterator, Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Final

SITE: Final = "https://site.financialmodelingprep.com"
ENTRY_PAGE: Final = f"{SITE}/developer/docs/stable/search-symbol"
PRICING_PAGE: Final = f"{SITE}/developer/docs/pricing"
DOCS_URL: Final = f"{SITE}/developer/docs/stable/"
BASE: Final = "https://financialmodelingprep.com/stable/"
USER_AGENT: Final = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
)
PRICING_PLANS: Final = ("Basic", "Starter", "Premium", "Ultimate")
PLAN_NAMES: Final = {
    "free": "Basic (free)",
    "IND1": "Starter",
    "IND2": "Premium",
    "IND3": "Ultimate",
    "COM1": "Commercial 1",
    "COM3": "Commercial 3",
}
JS_ESCAPES: Final = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "v": "\v", "0": "\0"}
JSON_TYPES: Final = {bool: "boolean", int: "number", float: "number", str: "string", list: "array", dict: "object"}

type Json = None | bool | int | float | str | list[Json] | dict[str, Json]
type Endpoint = dict[str, Json]


@dataclass(frozen=True, slots=True)
class Section:
    """A group of endpoints rendered together in ``endpoints.md``."""

    name: str
    purpose: str
    endpoint_ids: tuple[str, ...]


SECTIONS: Final = (
    Section(
        "Search",
        "Symbol and company search (search screen, default source)",
        ("search-symbol", "search-name", "search-exchange-variants", "search-company-screener"),
    ),
    Section(
        "Directory",
        "Symbol lists, exchanges and reference lists (exchange directory, sectors, security types)",
        (
            "company-symbols-list",
            "financial-symbols-list",
            "actively-trading-list",
            "ETFs-list",
            "available-exchanges",
            "available-sectors",
            "available-industries",
            "available-countries",
        ),
    ),
    Section(
        "Market hours",
        "Exchange time zones and trading hours (exchange table)",
        ("exchange-market-hours", "all-exchange-market-hours"),
    ),
    Section("Company", "Company profile (profile tab, security type, home exchange, logo URL)", ("profile-symbol",)),
    Section(
        "Quotes",
        "Prices for the watchlist and the detailed screen",
        ("quote", "quote-short", "batch-quote", "batch-quote-short", "full-exchange-quotes", "quote-change"),
    ),
    Section(
        "Financial statements",
        "Balance sheet tab and inputs for calculated ratios",
        (
            "balance-sheet-statement",
            "balance-sheet-statements-ttm",
            "as-reported-balance-statements",
            "income-statement",
            "cashflow-statement",
        ),
    ),
    Section(
        "Ratios and metrics",
        "Price and ratio tab, indicators",
        ("metrics-ratios-ttm", "metrics-ratios", "key-metrics-ttm", "key-metrics", "financial-scores"),
    ),
    Section("Dividends", "Dividends tab", ("dividends-company", "dividends-calendar")),
    Section(
        "Historical prices",
        "End-of-day prices (previous price, future charts)",
        ("historical-price-eod-light", "historical-price-eod-full"),
    ),
)


@dataclass(frozen=True, slots=True)
class FetchError:
    """A resource of the docs site that could not be downloaded."""

    url: str
    reason: str


@dataclass(frozen=True, slots=True)
class DocsData:
    """Everything extracted from the docs site."""

    endpoints: dict[str, Endpoint]
    responses: dict[str, Json]
    plans: dict[str, Json]
    pricing: dict[str, dict[str, str]]


def fetch(url: str) -> str | FetchError:
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,*/*", "Accept-Language": "en-US"}
    )
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


def unescape_js_string(raw: str) -> str:
    """Decode the body of a JavaScript string literal (escape sequences only, no quotes)."""

    def replace(match: re.Match[str]) -> str:
        escape = match.group(1)
        if escape[0] in "ux" and len(escape) > 1:
            return chr(int(escape[1:], 16))
        return JS_ESCAPES.get(escape, escape)

    return re.sub(r"\\(u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|.)", replace, raw, flags=re.DOTALL)


def embedded_json_literals(script: str) -> Iterator[Json]:
    """Yield every ``JSON.parse('...')`` literal of a bundle that decodes to valid JSON."""
    for match in re.finditer(r"JSON\.parse\('((?:[^'\\]|\\.)*)'\)", script):
        try:
            yield json.loads(unescape_js_string(match.group(1)))
        except json.JSONDecodeError:
            continue


def script_urls(page: str) -> list[str]:
    paths: list[str] = re.findall(r'src="(/_next/static/chunks/[^"]+\.js)"', page)
    return [SITE + path for path in paths]


def is_stable_endpoint(value: Json) -> bool:
    if not isinstance(value, dict) or "title" not in value:
        return False
    urls = value.get("urls")
    return isinstance(urls, list) and any(isinstance(url, str) and "/stable/" in url for url in urls)


def endpoint_metadata(blob: Mapping[str, Json]) -> dict[str, Endpoint]:
    """Collect stable endpoints from a ``{category: {group: {id: endpoint}}}`` or ``{category: {id: endpoint}}`` blob."""
    found: dict[str, Endpoint] = {}
    for category, groups in blob.items():
        if not isinstance(groups, dict):
            continue
        for group, entries in groups.items():
            if is_stable_endpoint(entries) and isinstance(entries, dict):
                found[group] = {**entries, "category": category, "group": category}
            elif isinstance(entries, dict):
                for endpoint_id, endpoint in entries.items():
                    if is_stable_endpoint(endpoint) and isinstance(endpoint, dict):
                        found[endpoint_id] = {**endpoint, "category": category, "group": group}
    return found


def is_response_blob(blob: Mapping[str, Json]) -> bool:
    return (
        bool(blob)
        and all(
            isinstance(value, list) or (isinstance(value, dict) and "title" not in value) for value in blob.values()
        )
        and not any(
            isinstance(value, dict) and any(isinstance(v, dict) for v in value.values()) for value in blob.values()
        )
    )


def classify_blobs(blobs: Iterable[Json]) -> tuple[dict[str, Endpoint], dict[str, Json], dict[str, Json]]:
    endpoints: dict[str, Endpoint] = {}
    responses: dict[str, Json] = {}
    plans: dict[str, Json] = {}
    for blob in blobs:
        if not isinstance(blob, dict):
            continue
        if "symbolSets" in blob:
            plans = blob
        elif is_response_blob(blob):
            responses.update(blob)
        else:
            endpoints.update(endpoint_metadata(blob))
    return endpoints, responses, plans


def page_text(page: str) -> str:
    page = re.sub(r"<script.*?</script>|<style.*?</style>", "", page, flags=re.DOTALL)
    text = html.unescape(re.sub(r"<[^>]+>", "\n", page))
    return re.sub(r"\n\s*\n+", "\n", text)


def parse_pricing(text: str) -> dict[str, dict[str, str]]:
    """Read lines like ``Stock Quote (Basic): Symbol Limited to ...`` into ``{data set: {plan: limit}}``."""
    matrix: dict[str, dict[str, str]] = {}
    pattern = re.compile(r"\s*(.+?) \((" + "|".join(PRICING_PLANS) + r")\): (.+)$")
    for line in text.splitlines():
        if match := pattern.match(line):
            name, plan, value = match.groups()
            matrix.setdefault(name, {})[plan] = value.rstrip(".")
    return matrix


def download() -> DocsData | FetchError:
    entry = fetch(ENTRY_PAGE)
    if isinstance(entry, FetchError):
        return entry
    blobs: list[Json] = []
    for url in script_urls(entry):
        script = fetch(url)
        if isinstance(script, FetchError):
            return script
        blobs.extend(embedded_json_literals(script))
    pricing = fetch(PRICING_PAGE)
    if isinstance(pricing, FetchError):
        return pricing
    endpoints, responses, plans = classify_blobs(blobs)
    return DocsData(endpoints, responses, plans, parse_pricing(page_text(pricing)))


def text_of(endpoint: Endpoint, key: str) -> str:
    value = endpoint.get(key)
    return value if isinstance(value, str) else ""


def example_url(endpoint: Endpoint) -> str:
    urls = endpoint.get("urls")
    return urls[0] if isinstance(urls, list) and urls and isinstance(urls[0], str) else ""


def endpoint_path(endpoint: Endpoint) -> str:
    return example_url(endpoint).replace(BASE, "/").split("?")[0]


def param_rows(endpoint: Endpoint) -> list[tuple[str, list[list[str]]]]:
    params = endpoint.get("params")
    if not isinstance(params, dict):
        return []
    tables: list[tuple[str, list[list[str]]]] = []
    for kind, table in params.items():
        rows = table.get("rows") if isinstance(table, dict) else None
        if isinstance(rows, list):
            tables.append((kind, [[cell_text(cell) for cell in row] for row in rows if isinstance(row, list)]))
    return tables


def cell_text(cell: Json) -> str:
    return ", ".join(str(item) for item in cell) if isinstance(cell, list) else str(cell)


def plan_entry(endpoint_id: str, endpoint: Endpoint, plans: Mapping[str, Json]) -> dict[str, Json]:
    entry = plans.get(text_of(endpoint, "responseId")) or plans.get(endpoint_id)
    return entry if isinstance(entry, dict) else {}


def response_example(endpoint: Endpoint, responses: Mapping[str, Json]) -> Json:
    return responses.get(text_of(endpoint, "responseId"))


def strip_html(text: str) -> str:
    text = re.sub(r"<li>\s*", "- ", text)
    text = re.sub(r"</?(p|ul|ol|br)[^>]*>", "\n", text)
    text = html.unescape(re.sub(r"<[^>]+>", "", text))
    return re.sub(r"\n\s*\n+", "\n\n", text).strip()


def anchor(title: str) -> str:
    return re.sub(r"[^a-z0-9 -]", "", title.lower()).replace(" ", "-")


def coverage_lines(entry: Mapping[str, Json]) -> list[str]:
    if not entry:
        return ["- No plan restrictions listed in the docs coverage table (available on all plans)."]
    lines: list[str] = []
    restricted = entry.get("restrictedPath")
    if isinstance(restricted, list) and restricted:
        lines.append("- Available only on: " + ", ".join(PLAN_NAMES.get(str(p), str(p)) for p in restricted))
    for plan, limits in entry.items():
        if plan != "restrictedPath" and isinstance(limits, dict):
            described = ", ".join(f"{param} = {value}" for param, value in limits.items())
            lines.append(f"- {PLAN_NAMES.get(plan, plan)}: {described}")
    return lines


def pricing_lines(title: str, pricing: Mapping[str, Mapping[str, str]]) -> list[str]:
    row = pricing.get(title, {})
    return [f"- Pricing page ({plan}): {row[plan]}" for plan in PRICING_PLANS if plan in row]


def params_markdown(endpoint: Endpoint) -> list[str]:
    tables = param_rows(endpoint)
    if not tables:
        return ["No parameters.", ""]
    lines: list[str] = []
    for kind, rows in tables:
        lines += [f"{kind.capitalize()} parameters (`*` = required):", "", "| Name | Type | Example |", "|---|---|---|"]
        lines += [f"| `{row[0]}` | {row[1]} | `{row[2]}` |" for row in rows if len(row) >= 3]
        lines.append("")
    return lines


def field_table(example: Json) -> list[str]:
    record = example[0] if isinstance(example, list) and example else example
    if not isinstance(record, dict):
        return []
    lines = ["| Field | JSON type (from example) | Example value |", "|---|---|---|"]
    for key, value in record.items():
        json_type = "null" if value is None else JSON_TYPES[type(value)]
        shown = json.dumps(value, ensure_ascii=False)
        shown = shown if len(shown) <= 60 else shown[:57] + "..."
        lines.append(f"| `{key}` | {json_type} | `{shown.replace('|', '/')}` |")
    return lines


def render_endpoint(endpoint_id: str, endpoint: Endpoint, data: DocsData) -> list[str]:
    example = response_example(endpoint, data.responses)
    page = text_of(endpoint, "pageURL") or endpoint_id
    lines = [
        f"### {text_of(endpoint, 'title')}",
        "",
        f"- Doc id: `{endpoint_id}` ({DOCS_URL}{page})",
        f"- Request: `GET {BASE.rstrip('/')}{endpoint_path(endpoint)}`",
        f"- Example: `{example_url(endpoint)}`",
    ]
    if example is not None:
        lines.append(f"- Example response: [`examples/{endpoint_id}.json`](examples/{endpoint_id}.json)")
    lines += ["", strip_html(text_of(endpoint, "content") or text_of(endpoint, "description")), ""]
    lines += params_markdown(endpoint)
    lines += ["Plan coverage:", "", *coverage_lines(plan_entry(endpoint_id, endpoint, data.plans))]
    lines += [*pricing_lines(text_of(endpoint, "title"), data.pricing), ""]
    if example is not None:
        lines += ["Response fields (first record of the documented example):", "", *field_table(example), ""]
    return lines


def endpoints_markdown(data: DocsData) -> str:
    lines = [
        "# FMP stable API - endpoints used by the screener",
        "",
        "Generated by `extract_docs.py` from the FMP documentation site (endpoint metadata, documented example",
        "responses and plan coverage tables). Example responses are FMP's documented samples, not responses",
        "recorded with our API key. See `README.md` for the base URL, authentication, plans and limits.",
        "",
        "## Contents",
        "",
    ]
    for section in SECTIONS:
        lines.append(f"- {section.name}: {section.purpose}")
        for endpoint_id in section.endpoint_ids:
            endpoint = data.endpoints[endpoint_id]
            title = text_of(endpoint, "title")
            lines.append(f"  - [{title}](#{anchor(title)}) `{endpoint_path(endpoint)}`")
    lines.append("")
    for section in SECTIONS:
        lines += [f"## {section.name}", "", section.purpose + ".", ""]
        for endpoint_id in section.endpoint_ids:
            lines += render_endpoint(endpoint_id, data.endpoints[endpoint_id], data)
    return "\n".join(lines)


def plans_markdown(data: DocsData) -> str:
    lines = [
        "# FMP plan matrix (personal use)",
        "",
        f"Copied from {PRICING_PAGE}.",
        "One row per data set as named on the pricing page; '-' means not listed.",
        "",
        "| Data set | Basic (free) | Starter | Premium | Ultimate |",
        "|---|---|---|---|---|",
    ]
    for name, row in data.pricing.items():
        cells = [row.get(plan, "-").replace("|", "/") for plan in PRICING_PLANS]
        lines.append(f"| {name} | " + " | ".join(cells) + " |")
    for title, key in (("Symbol sets", "symbolSets"), ("Exchange sets", "exchangeSets")):
        lines += ["", f"## {title} used by the coverage tables", ""]
        sets = data.plans.get(key)
        if isinstance(sets, dict):
            for name, members in sets.items():
                values = [str(member) for member in members] if isinstance(members, list) else []
                prefix = f"- `{name}` ({len(values)}): " if key == "symbolSets" else f"- `{name}`: "
                lines.append(prefix + ", ".join(values))
    return "\n".join(lines) + "\n"


def catalog(data: DocsData) -> list[dict[str, object]]:
    def sort_key(item: tuple[str, Endpoint]) -> tuple[str, str, str]:
        return text_of(item[1], "category"), text_of(item[1], "group"), item[0]

    return [
        {
            "id": endpoint_id,
            "category": text_of(endpoint, "category"),
            "group": text_of(endpoint, "group"),
            "title": text_of(endpoint, "title"),
            "path": endpoint_path(endpoint),
            "example": example_url(endpoint),
            "description": endpoint.get("description"),
            "params": [row for _, rows in param_rows(endpoint) for row in rows],
            "plans": plan_entry(endpoint_id, endpoint, data.plans),
            "docs": DOCS_URL + (text_of(endpoint, "pageURL") or endpoint_id),
        }
        for endpoint_id, endpoint in sorted(data.endpoints.items(), key=sort_key)
    ]


def missing_endpoints(data: DocsData) -> list[str]:
    return [i for section in SECTIONS for i in section.endpoint_ids if i not in data.endpoints]


def write_docs(data: DocsData, out: Path) -> None:
    examples = out / "examples"
    examples.mkdir(parents=True, exist_ok=True)
    for section in SECTIONS:
        for endpoint_id in section.endpoint_ids:
            example = response_example(data.endpoints[endpoint_id], data.responses)
            if example is not None:
                content = json.dumps(example, indent=2, ensure_ascii=False) + "\n"
                _ = (examples / f"{endpoint_id}.json").write_text(content, encoding="utf-8")
    _ = (out / "endpoints.md").write_text(endpoints_markdown(data), encoding="utf-8")
    _ = (out / "plans.md").write_text(plans_markdown(data), encoding="utf-8")
    catalog_json = json.dumps(catalog(data), indent=1, ensure_ascii=False) + "\n"
    _ = (out / "catalog.json").write_text(catalog_json, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    _ = parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent, help="output folder")
    out = Path(str(parser.parse_args().out))  # pyright: ignore[reportAny]  # rationale: argparse Namespace is untyped
    data = download()
    if isinstance(data, FetchError):
        print(f"Error: cannot download {data.url}: {data.reason}", file=sys.stderr)
        return 1
    if missing := missing_endpoints(data):
        print(f"Error: endpoints not found in the docs: {', '.join(missing)}", file=sys.stderr)
        return 1
    write_docs(data, out)
    print(f"{len(data.endpoints)} stable endpoints, {len(data.responses)} example responses -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
