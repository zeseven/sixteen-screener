# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Extract the Tiingo API documentation into local Markdown files.

The Tiingo website is an Angular application. Each documentation page is a component compiled into an
Ivy template function (``TgZ`` opens an element, ``_uU`` adds text, ``qZA`` closes the element, ...), and the
request and response field tables are arrays assigned in the component constructor. This tool downloads the
bundles, rebuilds each page's element tree and renders it as Markdown into ``pages/`` (API documentation),
``products/`` (product pages with plan details) and ``pricing.md``.

Usage: ``uv run docs/api/tiingo/extract_docs.py [--out DIR]``
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Iterator, Mapping, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Final

SITE: Final = "https://www.tiingo.com"
ASSETS: Final = "https://apimedia.tiingo.com/dist/"
ENTRY_PAGE: Final = f"{SITE}/documentation/general/overview"
USER_AGENT: Final = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
)
API_TOKEN_PLACEHOLDER: Final = "YOUR_API_TOKEN"
DOCS_MODULE: Final = "src_app_api_documentation_documentation_module_ts"
PRODUCTS_MODULE: Final = "src_app_api_products_products_module_ts"
PRICING_COMPONENTS: Final = ("tiingo-pricing-comparison", "tiingo-about-pricing")
SKIPPED_COMPONENTS: Final = frozenset(
    {"tiingo-documentation", "tiingo-api-token-dialog", "tiingo-api-token-container", "tiingo-doc-table"}
)
SKIPPED_TAB_LABELS: Final = frozenset({"Python", "Node", "PHP", "R", "Javascript", "JavaScript", "C#", "Java", "Go"})
SKIPPED_CLASSES: Final = frozenset({"documentation-breadcrumb-top-header"})
SKIPPED_TAGS: Final = frozenset({"img", "mat-icon", "svg", "button", "tiingo-api-token-container", "iframe", "video"})
INLINE_TAGS: Final = frozenset({"b", "strong", "i", "em", "a", "span", "code", "u", "sup", "sub", "br", "small"})
HEADING_LEVELS: Final = {"h1": 2, "h2": 3, "h3": 4, "h4": 5, "h5": 6, "h6": 6}
JS_ESCAPES: Final = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "v": "\v", "0": "\0"}
CLOSING: Final = {"(": ")", "[": "]", "{": "}"}

type JsValue = None | bool | int | float | str | list[JsValue] | dict[str, JsValue] | Expression


@dataclass(frozen=True, slots=True)
class Expression:
    """A JavaScript expression that is not a literal (kept as source text)."""

    source: str


@dataclass(frozen=True, slots=True)
class FetchError:
    """A resource of the website that could not be downloaded."""

    url: str
    reason: str


@dataclass(slots=True)
class Node:
    """An element or text node of a rendered template."""

    tag: str
    attributes: dict[str, str] = field(default_factory=dict)
    classes: frozenset[str] = frozenset()
    children: list[Node] = field(default_factory=list)
    text: str = ""
    table_rows: list[JsValue] | None = None


@dataclass(frozen=True, slots=True)
class Component:
    """A compiled Angular component of a bundle."""

    selector: str
    title: str
    template: Node


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


def unescape_js_string(raw: str) -> str:
    """Decode the body of a JavaScript string literal (escape sequences only, no quotes)."""

    def replace(match: re.Match[str]) -> str:
        escape = match.group(1)
        if escape[0] in "ux" and len(escape) > 1:
            return chr(int(escape[1:], 16))
        if escape == "\n":
            return ""
        return JS_ESCAPES.get(escape, escape)

    return re.sub(r"\\(u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|.)", replace, raw, flags=re.DOTALL)


def string_end(source: str, start: int) -> int:
    """Return the index after the string literal that starts at ``start``."""
    quote = source[start]
    index = start + 1
    while index < len(source):
        char = source[index]
        if char == "\\":
            index += 2
            continue
        if char == quote:
            return index + 1
        index += 1
    raise ValueError(f"unterminated string literal at {start}")


def matching_bracket(source: str, start: int) -> int:
    """Return the index of the bracket that closes the one at ``start``, skipping strings."""
    stack = [CLOSING[source[start]]]
    index = start + 1
    while index < len(source):
        char = source[index]
        if char in "\"'`":
            index = string_end(source, index)
            continue
        if char in CLOSING:
            stack.append(CLOSING[char])
        elif char in ")]}":
            if char != stack.pop():
                raise ValueError(f"unbalanced bracket at {index}")
            if not stack:
                return index
        index += 1
    raise ValueError(f"unclosed bracket at {start}")


def split_top_level(source: str, separator: str = ",") -> list[str]:
    parts: list[str] = []
    depth = 0
    current = 0
    index = 0
    while index < len(source):
        char = source[index]
        if char in "\"'`":
            index = string_end(source, index)
            continue
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == separator and depth == 0:
            parts.append(source[current:index])
            current = index + 1
        index += 1
    parts.append(source[current:])
    return [part.strip() for part in parts if part.strip()]


def parse_js_value(source: str) -> JsValue:
    """Parse a JavaScript literal (object, array, string, number, boolean); anything else is an Expression."""
    source = source.strip()
    if not source:
        return Expression(source)
    first = source[0]
    if first in "\"'" and string_end(source, 0) == len(source):
        return unescape_js_string(source[1:-1])
    if first == "[" and matching_bracket(source, 0) == len(source) - 1:
        return [parse_js_value(item) for item in split_top_level(source[1:-1])]
    if first == "{" and matching_bracket(source, 0) == len(source) - 1:
        return parse_js_object(source[1:-1])
    literals: dict[str, JsValue] = {"!0": True, "!1": False, "null": None, "true": True, "false": False}
    if source in literals:
        return literals[source]
    if re.fullmatch(r"-?\d+", source):
        return int(source)
    if re.fullmatch(r"-?\d*\.\d+(e-?\d+)?", source):
        return float(source)
    return Expression(source)


def parse_js_object(body: str) -> JsValue:
    result: dict[str, JsValue] = {}
    for entry in split_top_level(body):
        key_and_value = split_top_level(entry, ":")
        if len(key_and_value) < 2:
            return Expression("{" + body + "}")
        raw_key = key_and_value[0]
        value_source = entry[entry.index(":", len(raw_key) - 1 if raw_key[0] not in "\"'" else len(raw_key)) + 1 :]
        key = parse_js_value(raw_key) if raw_key[0] in "\"'" else raw_key
        result[str(key)] = parse_js_value(value_source)
    return result


@dataclass(frozen=True, slots=True)
class Call:
    """One template instruction call, e.g. ``s.TgZ(0,"div",1)``."""

    name: str
    arguments: list[str]


def instruction_calls(body: str) -> Iterator[Call]:
    """Yield the top-level ``x.Abc(...)`` calls of a template block in source order."""
    pattern = re.compile(r"\b[a-zA-Z_$][\w$]*\.([A-Za-z0-9_$]{3})\(")
    index = 0
    while match := pattern.search(body, index):
        open_paren = match.end() - 1
        close_paren = matching_bracket(body, open_paren)
        yield Call(match.group(1), split_top_level(body[open_paren + 1 : close_paren]))
        index = close_paren + 1


def template_blocks(template: str) -> tuple[str, str]:
    """Split a template function body into its creation (``1&e``) and update (``2&e``) blocks."""
    blocks: dict[str, str] = {}
    for match in re.finditer(r"([12])&\w&&\(", template):
        start = match.end() - 1
        if match.group(1) not in blocks:
            blocks[match.group(1)] = template[start + 1 : matching_bracket(template, start)]
    return blocks.get("1", ""), blocks.get("2", "")


def element_attributes(constant: JsValue) -> tuple[dict[str, str], frozenset[str]]:
    """Read static attributes and CSS classes from an Angular ``consts`` entry."""
    attributes: dict[str, str] = {}
    classes: set[str] = set()
    if not isinstance(constant, list):
        return attributes, frozenset()
    index = 0
    marker = 0
    while index < len(constant):
        item = constant[index]
        if isinstance(item, int) and not isinstance(item, bool):
            marker = item
            index += 1
        elif marker == 0 and isinstance(item, str) and index + 1 < len(constant):
            value = constant[index + 1]
            attributes[item] = value if isinstance(value, str) else ""
            index += 2
        else:
            if marker == 1 and isinstance(item, str):
                classes.add(item)
            index += 1
    return attributes, frozenset(classes)


def js_int(source: str) -> int:
    return int(float(source))


def readable_expression(source: str) -> str:
    """Shorten minified bindings such as ``o.xi3(1,2,null==t.a?null:t.a.b,"1.0-0")`` to ``a.b``."""
    source = re.sub(r"null==t\.(\w+)\?null:t\.\1\.(\w+)", r"\1.\2", source)
    source = re.sub(r"\b\w\.\w{3}\(\d+,\d+,(.*?)(?:,\"[^\"]*\")?\)$", r"\1", source)
    return source.removeprefix("t.")


def interpolated_text(arguments: Sequence[str]) -> str:
    """Join the literal parts of a text interpolation; bindings become placeholders."""
    parts: list[str] = []
    for argument in arguments:
        value = parse_js_value(argument)
        values = value if isinstance(value, list) else [value]
        for item in values:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, Expression):
                is_token = "apiToken" in item.source
                parts.append(API_TOKEN_PLACEHOLDER if is_token else "{" + readable_expression(item.source) + "}")
    return "".join(parts)


def build_tree(create: str, update: str, consts: list[JsValue], fields: Mapping[str, JsValue]) -> Node:
    root = Node("root")
    stack = [root]
    nodes: dict[int, Node] = {}
    for call in instruction_calls(create):
        if call.name in ("TgZ", "_UZ"):
            index, tag = js_int(call.arguments[0]), str(parse_js_value(call.arguments[1]))
            constant = (
                consts[js_int(call.arguments[2])] if len(call.arguments) > 2 and call.arguments[2].isdigit() else None
            )
            attributes, classes = element_attributes(constant)
            node = Node(tag, attributes, classes)
            stack[-1].children.append(node)
            nodes[index] = node
            if call.name == "TgZ":
                stack.append(node)
        elif call.name == "qZA" and len(stack) > 1:
            _ = stack.pop()
        elif call.name == "_uU":
            text = parse_js_value(call.arguments[1]) if len(call.arguments) > 1 else ""
            node = Node("#text", text=text if isinstance(text, str) else "")
            stack[-1].children.append(node)
            nodes[js_int(call.arguments[0])] = node
    apply_bindings(update, nodes, fields)
    return root


def apply_bindings(update: str, nodes: Mapping[int, Node], fields: Mapping[str, JsValue]) -> None:
    position = 0
    for call in instruction_calls(update):
        if call.name == "xp6":
            position += js_int(call.arguments[0]) if call.arguments else 1
            continue
        node = nodes.get(position)
        if node is None:
            continue
        if call.name == "Q6J" and parse_js_value(call.arguments[0]) == "tableRows":
            rows = fields.get(call.arguments[1].removeprefix("t."))
            node.table_rows = rows if isinstance(rows, list) else []
        elif node.tag == "#text":
            node.text = interpolated_text(call.arguments)


def component_fields(class_source: str) -> dict[str, JsValue]:
    """Read ``this.name=<literal>`` assignments of a component constructor."""
    fields: dict[str, JsValue] = {}
    for match in re.finditer(r"this\.(\w+)=(?=[\[{\"'])", class_source):
        start = match.end()
        end = (
            string_end(class_source, start)
            if class_source[start] in "\"'"
            else matching_bracket(class_source, start) + 1
        )
        fields[match.group(1)] = parse_js_value(class_source[start:end])
    return fields


def components(bundle: str) -> Iterator[Component]:
    for match in re.finditer(r'selectors:\[\["([\w-]+)"\]\]', bundle):
        selector = match.group(1)
        consts_match = re.compile(r"consts:(?=\[)").search(bundle, match.end())
        template_match = re.compile(r"template:function\(\w,\w\)\{").search(bundle, match.end())
        next_component = bundle.find("selectors:[[", match.end())
        if template_match is None or (0 <= next_component < template_match.start()):
            continue
        consts: list[JsValue] = []
        if consts_match and consts_match.start() < template_match.start():
            parsed = parse_js_value(bundle[consts_match.end() : matching_bracket(bundle, consts_match.end()) + 1])
            consts = parsed if isinstance(parsed, list) else []
        template_body = bundle[template_match.end() - 1 : matching_bracket(bundle, template_match.end() - 1) + 1]
        class_source = bundle[bundle.rfind("class ", 0, match.start()) : match.start()]
        title_match = re.search(r'setDocumentationHeader\("([^"]+)"\)|setCoreTitle\("([^"]+)"\)', class_source)
        title = next((group for group in title_match.groups() if group), selector) if title_match else selector
        create, update = template_blocks(template_body)
        yield Component(selector, title, build_tree(create, update, consts, component_fields(class_source)))


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text)


def table_markdown(rows: Sequence[JsValue], table_type: str) -> str:
    records = [row for row in rows if isinstance(row, dict)]
    if not records:
        return ""
    if table_type == "request":
        header = "| Field | Name | Required | Type | Description |\n|---|---|---|---|---|"
        keys = ("jsonFieldName", "name", "required", "jsonFieldType", "description")
    else:
        header = "| Field | Name | Type | Description |\n|---|---|---|---|"
        keys = ("jsonFieldName", "name", "jsonFieldType", "description")
    lines = [header]
    for record in records:
        cells = [table_cell(record.get(key)) for key in keys]
        cells[0] = f"`{cells[0]}`" if cells[0] else ""
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines)


def table_cell(value: JsValue) -> str:
    if isinstance(value, bool):
        return "yes" if value else "no"
    if value is None or isinstance(value, Expression):
        return ""
    text = re.sub(r"<li>", "; ", str(value))
    text = re.sub(r"<[^>]+>", "", text)
    return normalize_space(text).strip().replace("|", "/")


def render_inline(node: Node) -> str:
    if node.tag == "#text":
        return normalize_space(node.text)
    if node.tag in SKIPPED_TAGS:
        return ""
    inner = "".join(render_inline(child) for child in node.children)
    stripped = inner.strip()
    if not stripped:
        return inner
    match node.tag:
        case "b" | "strong":
            return f"**{stripped}**"
        case "i" | "em":
            return f"_{stripped}_"
        case "code":
            return f"`{stripped}`"
        case "a" if node.attributes.get("href", "").startswith("http"):
            return f"[{stripped}]({node.attributes['href']})"
        case _:
            return inner


def is_inline(node: Node) -> bool:
    return node.tag == "#text" or (node.tag in INLINE_TAGS and all(is_inline(child) for child in node.children))


def render_block(node: Node) -> list[str]:
    if node.tag in SKIPPED_TAGS or node.classes & SKIPPED_CLASSES:
        return []
    if node.table_rows is not None:
        table = table_markdown(node.table_rows, node.attributes.get("tableType", "response"))
        return [table] if table else []
    if node.tag == "pre":
        code = "".join(child.text for child in node.children if child.tag == "#text").strip("\n")
        return [f"```\n{code}\n```"] if code.strip() else []
    if node.tag in HEADING_LEVELS:
        text = "".join(render_inline(child) for child in node.children).strip()
        return [f"{'#' * HEADING_LEVELS[node.tag]} {text}"] if text else []
    if node.tag == "mat-tab":
        label = node.attributes.get("label", "")
        if label in SKIPPED_TAB_LABELS:
            return []
        content = render_children(node)
        return [f"**{label}**", *content] if content and label else content
    if node.tag == "li":
        text = " ".join(render_children(node)).strip()
        return [f"- {text}"] if text else []
    if node.tag in ("ul", "ol"):
        items = render_children(node)
        return ["\n".join(items)] if items else []
    if node.tag == "tr":
        cells = [" ".join(render_children(cell)).strip().replace("|", "/") for cell in node.children]
        return ["| " + " | ".join(cells) + " |"] if any(cells) else []
    if node.tag == "table":
        return table_block(node)
    return render_children(node)


def table_block(node: Node) -> list[str]:
    rows = [line for child in node.children for line in render_table_rows(child)]
    if not rows:
        return []
    columns = rows[0].count("|") - 1
    return ["\n".join([rows[0], "|" + "---|" * columns, *rows[1:]])]


def render_table_rows(node: Node) -> list[str]:
    if node.tag == "tr":
        return render_block(node)
    return [line for child in node.children for line in render_table_rows(child)]


def render_children(node: Node) -> list[str]:
    blocks: list[str] = []
    inline: list[str] = []

    def flush() -> None:
        text = "".join(inline).strip()
        if text:
            blocks.append(text)
        inline.clear()

    for child in node.children:
        if is_inline(child):
            inline.append(render_inline(child))
        else:
            flush()
            blocks.extend(render_block(child))
    flush()
    return blocks


def component_markdown(component: Component, source_url: str) -> str:
    blocks = render_children(component.template)
    deduplicated = [block for index, block in enumerate(blocks) if index == 0 or block != blocks[index - 1]]
    header = [f"# {component.title}", "", f"Source: {source_url} (component `{component.selector}`)."]
    return "\n\n".join(["\n".join(header), *deduplicated]) + "\n"


def bundle_urls(entry_page: str, runtime: str) -> dict[str, str]:
    """Map bundle names (``main``, lazy module names) to their URLs."""
    sources: list[str] = re.findall(r'src="(https://apimedia\.tiingo\.com/dist/[^"]+-es2015\.[^"]+\.js)"', entry_page)
    urls = {re.sub(r"-es2015\..*", "", Path(src).name): src for src in sources}
    chunk_map = re.search(r"r\.u=function\(e\)\{return e\+\"-es2015\.\"\+(\{[^}]+\})", runtime)
    chunks = parse_js_value(chunk_map.group(1)) if chunk_map else {}
    if isinstance(chunks, dict):
        for name, digest in chunks.items():
            urls[name] = f"{ASSETS}{name}-es2015.{digest}.js"
    return urls


def download_bundles() -> dict[str, str] | FetchError:
    entry = fetch(ENTRY_PAGE)
    if isinstance(entry, FetchError):
        return entry
    initial = bundle_urls(entry, "")
    if "runtime" not in initial:
        return FetchError(ENTRY_PAGE, "runtime bundle not found")
    runtime = fetch(initial["runtime"])
    if isinstance(runtime, FetchError):
        return runtime
    urls = bundle_urls(entry, runtime)
    bundles: dict[str, str] = {}
    for name in ("main", DOCS_MODULE, PRODUCTS_MODULE):
        if name not in urls:
            return FetchError(ENTRY_PAGE, f"bundle {name} not found")
        bundle = fetch(urls[name])
        if isinstance(bundle, FetchError):
            return bundle
        bundles[name] = bundle
    return bundles


def page_name(selector: str) -> str:
    return selector.removeprefix("tiingo-")


def write_docs(bundles: Mapping[str, str], out: Path) -> int:
    written = 0
    targets = (
        (DOCS_MODULE, out / "pages", f"{SITE}/documentation/"),
        (PRODUCTS_MODULE, out / "products", f"{SITE}/products/"),
    )
    for bundle_name, folder, source in targets:
        folder.mkdir(parents=True, exist_ok=True)
        for component in components(bundles[bundle_name]):
            if component.selector in SKIPPED_COMPONENTS:
                continue
            path = folder / f"{page_name(component.selector)}.md"
            _ = path.write_text(component_markdown(component, source), encoding="utf-8")
            written += 1
    pricing = [c for c in components(bundles["main"]) if c.selector in PRICING_COMPONENTS]
    content = "\n".join(component_markdown(component, f"{SITE}/about/pricing") for component in pricing)
    _ = (out / "pricing.md").write_text(content, encoding="utf-8")
    return written + 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    _ = parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent, help="output folder")
    out = Path(str(parser.parse_args().out))  # pyright: ignore[reportAny]  # rationale: argparse Namespace is untyped
    bundles = download_bundles()
    if isinstance(bundles, FetchError):
        print(f"Error: cannot download {bundles.url}: {bundles.reason}", file=sys.stderr)
        return 1
    print(f"{write_docs(bundles, out)} files -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
