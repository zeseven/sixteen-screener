import json

from extract_docs import (
    ApiFunction,
    Example,
    Parameter,
    clean_text,
    demo_note,
    field_record,
    field_table,
    html_lines,
    page_lines,
    parse_docs,
    response_shape,
    sample_content,
    trimmed,
)

DOCS_PAGE = """
<h2 id="fundamentals">Fundamental Data</h2>
<p>Intro of the category.</p>
<h4 id="company-overview">Company Overview <span class="popular-label">Trending</span>
<span class="premium-label">Premium</span></h4>
<p>This API returns the company information.</p>
<h6><b>API Parameters</b></h6>
<p><b>❚ Required: <code>function</code></b></p>
<p>The function of your choice. In this case, <code>function=OVERVIEW</code> </p>
<p>❚ Optional: <code>datatype</code></p>
<p>By default, <code>datatype=json</code>.</p>
<h6><b>Examples (click for JSON output)</b></h6>
<p>Sample ticker traded in UK - London Stock Exchange</p>
<p><a href="https://www.alphavantage.co/query?function=OVERVIEW&amp;symbol=TSCO.LON&amp;apikey=demo">
<code>https://www.alphavantage.co/query?<b>function</b>=OVERVIEW&amp;<b>symbol</b>=TSCO.LON&amp;<b>apikey</b>=demo</code></a></p>
<div class="premium-msg"><p>\U0001f4a1 Tip: this is a premium API function.</p></div>
<h6><b>Language-specific guides</b></h6>
<pre><code>print("ignored")</code></pre>
<h4 id="company-logo">Company Logo</h4>
<p>This API returns the logo.</p>
"""


def test_clean_text_strips_tags_entities_and_decorations() -> None:
    assert clean_text("<b>❚ Required:</b> <code>a&amp;b</code>\n  x") == "Required: a&b x"


def test_html_lines_splits_blocks_and_drops_scripts() -> None:
    assert html_lines("<p>one</p><script>x()</script><div>two<br>three</div>") == ["one", "two", "three"]


def test_parse_docs_reads_function_sections() -> None:
    overview, logo = parse_docs(DOCS_PAGE)

    assert overview == ApiFunction(
        anchor="company-overview",
        category="Fundamental Data",
        title="Company Overview",
        labels=["Trending", "Premium"],
        functions=["OVERVIEW"],
        description=["This API returns the company information."],
        parameters=[
            Parameter("function", True, "The function of your choice. In this case, function=OVERVIEW"),
            Parameter("datatype", False, "By default, datatype=json."),
        ],
        examples=[
            Example(
                "https://www.alphavantage.co/query?function=OVERVIEW&symbol=TSCO.LON&apikey=demo",
                "Sample ticker traded in UK - London Stock Exchange",
            )
        ],
        notes=["Tip: this is a premium API function."],
    )
    assert logo.title == "Company Logo"
    assert logo.labels == []
    assert logo.description == ["This API returns the logo."]


def test_page_lines_returns_text_between_markers() -> None:
    page = "<p>intro</p><p>Limits?</p><p>25 per day</p><p>Next question</p>"
    assert page_lines(page, "Limits", "Next") == ["Limits?", "25 per day"]


def test_trimmed_shortens_lists_and_date_series() -> None:
    series = {f"2026-01-0{day}": {"close": day} for day in range(1, 6)}
    value = {"meta": {"symbol": "IBM"}, "series": series, "reports": [1, 2, 3, 4]}
    assert trimmed(value) == {
        "meta": {"symbol": "IBM"},
        "series": {"2026-01-01": {"close": 1}, "2026-01-02": {"close": 2}, "2026-01-03": {"close": 3}},
        "reports": [1, 2],
    }


def test_sample_content_formats_json_and_cuts_csv() -> None:
    assert sample_content('{"a":[1,2,3]}') == '{\n  "a": [\n    1,\n    2\n  ]\n}\n'
    csv = "\n".join(f"line{index}" for index in range(30))
    assert sample_content(csv).splitlines() == [f"line{index}" for index in range(20)]


def test_demo_note_detects_messages_without_data() -> None:
    assert demo_note(json.dumps({"Information": "premium only"})) == "premium only"
    assert demo_note(json.dumps({"symbol": "IBM"})) is None
    assert demo_note("symbol,name") is None


def test_field_record_finds_the_representative_record() -> None:
    assert field_record({"symbol": "IBM", "annualReports": [{"a": 1}]}) == {"a": 1}
    assert field_record({"Global Quote": {"05. price": "1"}}) == {"05. price": "1"}
    assert field_record({"Meta Data": {"x": 1}, "Series": {"2026-01-02": {"close": "2"}}}) == {"close": "2"}
    assert field_record({"Symbol": "IBM"}) == {"Symbol": "IBM"}
    assert field_record([]) is None


def test_field_table_and_response_shape() -> None:
    example = {"symbol": "IBM", "data": [{"amount": "1.69", "flag": None}]}
    assert field_table(example)[2:] == ['| `amount` | string | `"1.69"` |', "| `flag` | null | `null` |"]
    assert response_shape(example) == "{symbol, data[]}"
    assert response_shape([1]) == "[ ... ]"
