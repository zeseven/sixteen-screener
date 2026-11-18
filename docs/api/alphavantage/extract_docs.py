# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Extract the Alpha Vantage API documentation into local Markdown and JSON files.

The Alpha Vantage documentation is one server-rendered HTML page: an ``h2`` per category and an ``h4`` per
API function with its labels (Premium, Trending, Utility), description, parameters, example URLs and tips. The
examples use the public ``demo`` API key, so their responses can be downloaded as documented samples. This tool
regenerates ``endpoints.md``, ``examples/``, ``plans.md`` and ``catalog.json`` in this folder.

Usage: ``uv run docs/api/alphavantage/extract_docs.py [--out DIR] [--skip-examples]``
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
from collections.abc import Iterator, Sequence
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Final

SITE: Final = "https://www.alphavantage.co"
DOCS_PAGE: Final = f"{SITE}/documentation/"
PREMIUM_PAGE: Final = f"{SITE}/premium/"
SUPPORT_PAGE: Final = f"{SITE}/support/"
USER_AGENT: Final = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
)
DEMO_REQUEST_DELAY_SECONDS: Final = 2.0
CSV_SAMPLE_LINES: Final = 20
SAMPLE_LIST_ITEMS: Final = 2
SAMPLE_SERIES_ENTRIES: Final = 3
DATE_KEY: Final = re.compile(r"\d{4}-\d{2}-\d{2}")
DECORATIONS: Final = ("❚", "\U0001f4a1", "✨", "\U0001f3db")
LABEL_CLASSES: Final = {"premium-label": "Premium", "popular-label": "Trending", "utility-label": "Utility"}
JSON_TYPES: Final = {bool: "boolean", int: "number", float: "number", str: "string", list: "array", dict: "object"}

type Json = None | bool | int | float | str | list[Json] | dict[str, Json]


@dataclass(frozen=True, slots=True)
class Section:
    """A group of API functions rendered together in ``endpoints.md`` (by docs anchor id)."""

    name: str
    purpose: str
    anchors: tuple[str, ...]


SECTIONS: Final = (
    Section(
        "Search and directory",
        "Search screen, exchange directory and market status",
        ("symbolsearch", "listing-status", "market-status"),
    ),
    Section(
        "Quotes and prices",
        "Prices for the watchlist and the detailed screen, previous price",
        ("latestprice", "realtime-bulk-quotes", "daily", "dailyadj"),
    ),
    Section(
        "Company",
        "Company profile, ratios, security type and logo",
        ("company-overview", "etf-profile", "company-logo", "shares-outstanding"),
    ),
    Section(
        "Financial statements",
        "Balance sheet tab and inputs for calculated ratios",
        ("balance-sheet", "income-statement", "cash-flow", "earnings"),
    ),
    Section("Corporate actions", "Dividends tab", ("dividends", "splits")),
)


@dataclass(frozen=True, slots=True)
class FetchError:
    """A resource that could not be downloaded."""

    url: str
    reason: str


@dataclass(frozen=True, slots=True)
class Parameter:
    """A query parameter of an API function."""

    name: str
    required: bool
    description: str


@dataclass(frozen=True, slots=True)
class Example:
    """An example request URL, with the caption shown above it in the docs."""

    url: str
    caption: str


@dataclass(slots=True)
class ApiFunction:
    """One documented API function (an ``h4`` section of the docs page)."""

    anchor: str
    category: str
    title: str
    labels: list[str]
    functions: list[str]
    description: list[str] = field(default_factory=list)
    parameters: list[Parameter] = field(default_factory=list)
    examples: list[Example] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


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


def clean_text(fragment: str) -> str:
    text = html.unescape(re.sub(r"<[^>]+>", "", fragment))
    for decoration in DECORATIONS:
        text = text.replace(decoration, "")
    return re.sub(r"\s+", " ", text).strip()


def html_lines(fragment: str) -> list[str]:
    """Split an HTML fragment into its text lines (one per block element)."""
    fragment = re.sub(r"<(script|style)\b.*?</\1>", "", fragment, flags=re.DOTALL)
    fragment = re.sub(r"</?(p|div|h\d|br|li|ul|ol|tr)\b[^>]*>", "\n", fragment)
    return [line for line in (clean_text(part) for part in fragment.split("\n")) if line]


def section_sources(page: str) -> Iterator[tuple[str, str, str]]:
    """Yield ``(category, h4 html, body html)`` for every API function of the docs page."""
    heading = re.compile(r'<h2 id="[^"]+">(.*?)</h2>|<h4 id="[^"]+">.*?</h4>', re.DOTALL)
    matches = list(heading.finditer(page))
    category = ""
    for index, match in enumerate(matches):
        if match.group(1) is not None:
            category = clean_text(match.group(1))
            continue
        end = matches[index + 1].start() if index + 1 < len(matches) else len(page)
        body = page[match.end() : end]
        guides = body.find("Language-specific")
        yield category, match.group(0), body[: guides if guides >= 0 else len(body)]


def parse_function(category: str, heading: str, body: str) -> ApiFunction:
    anchor_match = re.match(r'<h4 id="([^"]+)">(.*?)</h4>', heading, re.DOTALL)
    if anchor_match is None:
        raise ValueError(f"not an API function heading: {heading[:80]}")
    title_html = anchor_match.group(2)
    label_classes: list[str] = re.findall(r'<span class="([a-z-]+)">', title_html)
    labels = [LABEL_CLASSES[label] for label in label_classes if label in LABEL_CLASSES]
    title = clean_text(re.sub(r"<span.*?</span>", "", title_html, flags=re.DOTALL))
    functions = list(dict.fromkeys(re.findall(r"function=([A-Z][A-Z0-9_]+)", body)))
    api_function = ApiFunction(anchor_match.group(1), category, title, labels, functions)
    hrefs: list[str] = re.findall(r'<a href="(https://www\.alphavantage\.co/query\?[^"]+)"', body)
    example_urls = [html.unescape(url) for url in hrefs]
    fill_from_lines(api_function, html_lines(body), example_urls)
    return api_function


def fill_from_lines(api_function: ApiFunction, lines: Sequence[str], example_urls: Sequence[str]) -> None:
    """Assign the text lines of a function section to description, parameters, examples and notes."""
    state = "description"
    caption = ""
    pending: list[tuple[str, bool]] = []
    for line in lines:
        if line == "API Parameters":
            state = "parameters"
        elif match := re.match(r"(Required|Optional): (\S+)$", line):
            pending.append((match.group(2), match.group(1) == "Required"))
            state = "parameter"
        elif line.startswith("Example"):
            state = "examples"
        elif line.startswith(("Tip:", "*", "Note:")):
            api_function.notes.append(line)
        elif state == "description":
            api_function.description.append(line)
        elif state == "parameter" and pending:
            name, required = pending.pop()
            api_function.parameters.append(Parameter(name, required, line))
        elif state == "examples" and line.startswith("https://"):
            url = next((candidate for candidate in example_urls if same_url(candidate, line)), line)
            api_function.examples.append(Example(url, caption))
            caption = ""
        elif state == "examples":
            caption = line.rstrip(":")
    api_function.parameters.extend(Parameter(name, required, "") for name, required in pending)


def same_url(left: str, right: str) -> bool:
    return left.replace(" ", "") == right.replace(" ", "")


def parse_docs(page: str) -> list[ApiFunction]:
    return [parse_function(category, heading, body) for category, heading, body in section_sources(page)]


def page_lines(page: str, start: str, stop: str) -> list[str]:
    """Return the text lines of a page between the first line containing ``start`` and the one containing ``stop``."""
    lines = html_lines(page)
    first = next((index for index, line in enumerate(lines) if start in line), 0)
    last = next((index for index, line in enumerate(lines) if index > first and stop in line), len(lines))
    return lines[first:last]


def demo_example(api_function: ApiFunction) -> Example | None:
    return next((example for example in api_function.examples if "apikey=demo" in example.url), None)


def example_file_name(api_function: ApiFunction, content: str) -> str:
    extension = "json" if content.lstrip().startswith(("{", "[")) else "csv"
    return f"{api_function.anchor}.{extension}"


def decode_json(text: str) -> Json:
    """Decode JSON text; raises ``json.JSONDecodeError`` like ``json.loads``."""
    value: Json = json.loads(text)  # pyright: ignore[reportAny]  # rationale: json.loads is untyped, JSON maps to Json
    return value


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


def sample_content(content: str) -> str:
    stripped = content.lstrip()
    if stripped.startswith(("{", "[")):
        try:
            return json.dumps(trimmed(decode_json(content)), indent=2, ensure_ascii=False) + "\n"
        except json.JSONDecodeError:
            return content
    return "\n".join(content.splitlines()[:CSV_SAMPLE_LINES]) + "\n"


def demo_note(content: str) -> str | None:
    """Return the message of a demo response that carries no data (premium or rate-limit notices)."""
    try:
        decoded = decode_json(content)
    except json.JSONDecodeError:
        return None
    if isinstance(decoded, dict) and len(decoded) == 1:
        [(key, value)] = decoded.items()
        if key in ("Information", "Note", "Error Message") and isinstance(value, str):
            return value
    return None


def field_record(example: Json) -> dict[str, Json] | None:
    """Pick the record that best shows the response fields: the first item of the first list of objects."""
    if isinstance(example, list):
        first = example[0] if example else None
        return first if isinstance(first, dict) else None
    if not isinstance(example, dict):
        return None
    for value in example.values():
        if isinstance(value, list) and value and isinstance(value[0], dict):
            return value[0]
        if is_date_series(value) and isinstance(value, dict):
            first = next(iter(value.values()))
            return first if isinstance(first, dict) else None
    nested = [value for value in example.values() if isinstance(value, dict)]
    if len(example) == 1 and nested:
        return field_record(nested[0]) or nested[0]
    return example


def field_table(example: Json) -> list[str]:
    record = field_record(example)
    if not record:
        return []
    lines = ["| Field | JSON type (from example) | Example value |", "|---|---|---|"]
    for key, value in record.items():
        json_type = "null" if value is None else JSON_TYPES[type(value)]
        shown = json.dumps(value, ensure_ascii=False)
        shown = shown if len(shown) <= 60 else shown[:57] + "..."
        lines.append(f"| `{key}` | {json_type} | `{shown.replace('|', '/')}` |")
    return lines


def response_shape(example: Json) -> str:
    """Describe the top level of a response, e.g. ``{symbol, annualReports[], quarterlyReports[]}``."""
    if isinstance(example, list):
        return "[ ... ]"
    if not isinstance(example, dict):
        return ""
    parts = [
        f"{key}[]" if isinstance(value, list) else f"{key}{{}}" if isinstance(value, dict) else key
        for key, value in example.items()
    ]
    return "{" + ", ".join(parts) + "}"


def render_function(api_function: ApiFunction, examples: dict[str, str]) -> list[str]:
    names = ", ".join(f"`{name}`" for name in api_function.functions)
    lines = [
        f"### {api_function.title}",
        "",
        f"- Docs: {DOCS_PAGE}#{api_function.anchor}",
        f"- Function: {names}",
        f"- Labels: {', '.join(api_function.labels) if api_function.labels else 'none (free)'}",
    ]
    file_name = examples.get(api_function.anchor)
    if file_name:
        lines.append(f"- Demo response: [`examples/{file_name}`](examples/{file_name})")
    lines += ["", *api_function.description, ""]
    lines += ["| Parameter | Required | Description |", "|---|---|---|"]
    lines += [
        f"| `{p.name}` | {'yes' if p.required else 'no'} | {p.description.replace('|', '/')} |"
        for p in api_function.parameters
    ]
    lines += ["", "Examples:", ""]
    lines += [
        f"- {example.caption + ': ' if example.caption else ''}`{example.url}`" for example in api_function.examples
    ]
    if api_function.notes:
        lines += ["", "Notes:", "", *[f"- {note}" for note in api_function.notes]]
    lines.append("")
    return lines


def render_example_fields(out: Path, file_name: str) -> list[str]:
    if not file_name.endswith(".json"):
        return []
    example = decode_json((out / "examples" / file_name).read_text(encoding="utf-8"))
    note = demo_note(json.dumps(example))
    if note:
        return [f'Demo response carries no data: "{note}"', ""]
    lines: list[str] = []
    message = example.get("message") if isinstance(example, dict) else None
    if isinstance(message, str):
        lines += [f'Demo response message: "{message}"', ""]
    if field_record(example) is not example:
        lines += [f"Response shape: `{response_shape(example)}`", ""]
    table = field_table(example)
    return [*lines, "Fields (first record of the demo response):", "", *table, ""] if table else lines


def endpoints_markdown(functions: dict[str, ApiFunction], examples: dict[str, str], out: Path) -> str:
    lines = [
        "# Alpha Vantage API - functions used by the screener",
        "",
        "Generated by `extract_docs.py` from the Alpha Vantage documentation page. Demo responses were downloaded",
        "from the documented example URLs (public `demo` key); they are samples, not responses recorded with our key.",
        "See `README.md` for authentication and `FREE-TIER.md` for the free plan.",
        "",
        "## Contents",
        "",
    ]
    for section in SECTIONS:
        lines.append(f"- {section.name}: {section.purpose}")
        lines += [
            f"  - [{functions[a].title}](#{anchor_of(functions[a].title)}) {', '.join(functions[a].labels)}".rstrip()
            for a in section.anchors
        ]
    lines.append("")
    for section in SECTIONS:
        lines += [f"## {section.name}", "", section.purpose + ".", ""]
        for anchor in section.anchors:
            lines += render_function(functions[anchor], examples)
            lines += render_example_fields(out, examples.get(anchor, ""))
    return "\n".join(lines)


def anchor_of(title: str) -> str:
    return re.sub(r"[^a-z0-9 _-]", "", title.lower()).replace(" ", "-")


def plans_markdown(premium_page: str, support_page: str) -> str:
    plans = [line for line in html_lines(premium_page) if re.match(r"\d+ requests/min", line)]
    limits = page_lines(support_page, "Are there usage/frequency limits", "Why is your realtime")
    realtime = page_lines(support_page, "Why is your realtime", "You support both raw")
    lines = [
        "# Alpha Vantage plans",
        "",
        f"Copied from {PREMIUM_PAGE} and {SUPPORT_PAGE}.",
        "",
        "## Free usage",
        "",
        *limits,
        "",
        "## Premium plans (first five monthly, then annual)",
        "",
        *[f"- {plan}" for plan in plans],
        "",
        "Premium features listed on the subscription page: no daily limits, all premium functions unlocked.",
        "",
        "## Realtime data",
        "",
        *realtime,
    ]
    return "\n".join(lines) + "\n"


def catalog(functions: Sequence[ApiFunction]) -> list[dict[str, object]]:
    return [asdict(api_function) for api_function in functions]


def missing_anchors(functions: dict[str, ApiFunction]) -> list[str]:
    return [anchor for section in SECTIONS for anchor in section.anchors if anchor not in functions]


def download_examples(functions: dict[str, ApiFunction], out: Path) -> dict[str, str] | FetchError:
    folder = out / "examples"
    folder.mkdir(parents=True, exist_ok=True)
    written: dict[str, str] = {}
    for section in SECTIONS:
        for anchor in section.anchors:
            example = demo_example(functions[anchor])
            if example is None:
                continue
            content = fetch(example.url)
            if isinstance(content, FetchError):
                return content
            file_name = example_file_name(functions[anchor], content)
            _ = (folder / file_name).write_text(sample_content(content), encoding="utf-8")
            written[anchor] = file_name
            time.sleep(DEMO_REQUEST_DELAY_SECONDS)
    return written


def existing_examples(out: Path) -> dict[str, str]:
    folder = out / "examples"
    return {path.stem: path.name for path in folder.glob("*.*")} if folder.exists() else {}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    _ = parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent, help="output folder")
    _ = parser.add_argument("--skip-examples", action="store_true", help="keep the existing demo responses")
    arguments = parser.parse_args()
    out = Path(str(arguments.out))  # pyright: ignore[reportAny]  # rationale: argparse Namespace is untyped
    skip_examples = bool(arguments.skip_examples)  # pyright: ignore[reportAny]  # rationale: same
    pages: list[str] = []
    for url in (DOCS_PAGE, PREMIUM_PAGE, SUPPORT_PAGE):
        page = fetch(url)
        if isinstance(page, FetchError):
            print(f"Error: cannot download {page.url}: {page.reason}", file=sys.stderr)
            return 1
        pages.append(page)
    docs_page, premium_page, support_page = pages
    parsed = parse_docs(docs_page)
    functions = {api_function.anchor: api_function for api_function in parsed}
    if missing := missing_anchors(functions):
        print(f"Error: functions not found in the docs: {', '.join(missing)}", file=sys.stderr)
        return 1
    out.mkdir(parents=True, exist_ok=True)
    examples = existing_examples(out) if skip_examples else download_examples(functions, out)
    if isinstance(examples, FetchError):
        print(f"Error: cannot download {examples.url}: {examples.reason}", file=sys.stderr)
        return 1
    _ = (out / "endpoints.md").write_text(endpoints_markdown(functions, examples, out), encoding="utf-8")
    _ = (out / "plans.md").write_text(plans_markdown(premium_page, support_page), encoding="utf-8")
    catalog_json = json.dumps(catalog(parsed), indent=1, ensure_ascii=False) + "\n"
    _ = (out / "catalog.json").write_text(catalog_json, encoding="utf-8")
    print(f"{len(parsed)} API functions, {len(examples)} demo responses -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
