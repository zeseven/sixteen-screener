import json

from extract_docs import (
    DocsData,
    classify_blobs,
    coverage_lines,
    embedded_json_literals,
    endpoint_path,
    field_table,
    parse_pricing,
    script_urls,
    unescape_js_string,
)

QUOTE_ENDPOINT = {
    "title": "Stock Quote",
    "urls": ["https://financialmodelingprep.com/stable/quote?symbol=AAPL"],
    "responseId": "quote",
}
LEGACY_ENDPOINT = {"title": "Old Quote", "urls": ["https://financialmodelingprep.com/api/v3/quote/AAPL"]}


def test_unescape_js_string_decodes_escapes() -> None:
    assert unescape_js_string(r"a\'b\ncé\x41\\") == "a'b\ncéA\\"


def test_embedded_json_literals_skips_invalid_json() -> None:
    script = "a=JSON.parse('{\"k\":\"it\\'s\"}');b=JSON.parse('{oops}')"
    assert list(embedded_json_literals(script)) == [{"k": "it's"}]


def test_script_urls_returns_absolute_chunk_urls() -> None:
    page = '<script src="/_next/static/chunks/main-1.js"></script><script src="https://x/y.js"></script>'
    assert script_urls(page) == ["https://site.financialmodelingprep.com/_next/static/chunks/main-1.js"]


def test_classify_blobs_separates_metadata_responses_and_plans() -> None:
    metadata = {"Quotes": {"Single Quote": {"quote": QUOTE_ENDPOINT, "old": LEGACY_ENDPOINT}}}
    flat_metadata = {"Directory": {"stock-list": {**QUOTE_ENDPOINT, "responseId": "stock-list"}}}
    responses = {"quote": [{"symbol": "AAPL"}], "available-sectors": [{"sector": "Energy"}]}
    plans = {"symbolSets": {"sample-company": ["AAPL"]}, "quote": {"free": {"symbol": "sample-company"}}}

    endpoints, found_responses, found_plans = classify_blobs([metadata, flat_metadata, responses, plans, ["x"]])

    assert set(endpoints) == {"quote", "stock-list"}
    assert endpoints["quote"]["category"] == "Quotes"
    assert endpoints["quote"]["group"] == "Single Quote"
    assert endpoints["stock-list"]["group"] == "Directory"
    assert found_responses == responses
    assert found_plans == plans


def test_endpoint_path_strips_base_and_query() -> None:
    assert endpoint_path(QUOTE_ENDPOINT) == "/quote"
    assert endpoint_path({"title": "No urls"}) == ""


def test_parse_pricing_reads_plan_rows() -> None:
    text = "Stock Quote (Basic): Symbol Limited to AAPL.\nStock Quote (Ultimate): Full Global Coverage.\nnoise"
    assert parse_pricing(text) == {
        "Stock Quote": {"Basic": "Symbol Limited to AAPL", "Ultimate": "Full Global Coverage"}
    }


def test_coverage_lines_describe_restrictions() -> None:
    entry = {"free": {"symbol": "sample-company", "limit": 5}, "restrictedPath": ["IND2", "IND3"]}
    assert coverage_lines(entry) == [
        "- Available only on: Premium, Ultimate",
        "- Basic (free): symbol = sample-company, limit = 5",
    ]
    assert coverage_lines({}) == ["- No plan restrictions listed in the docs coverage table (available on all plans)."]


def test_field_table_uses_first_record_and_truncates_long_values() -> None:
    lines = field_table([{"symbol": "AAPL", "price": 1.5, "note": None, "text": "x" * 80}])
    assert lines[2] == '| `symbol` | string | `"AAPL"` |'
    assert lines[3] == "| `price` | number | `1.5` |"
    assert lines[4] == "| `note` | null | `null` |"
    assert lines[5].endswith('..." |') or lines[5].endswith("...` |")
    assert field_table("not a record") == []


def test_docs_data_is_json_serializable_catalog_input() -> None:
    data = DocsData({"quote": QUOTE_ENDPOINT}, {}, {}, {})
    assert json.dumps(data.endpoints)
