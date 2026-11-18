from extract_docs import (
    DemoRequest,
    cell_value,
    demo_url,
    openapi_problem,
    pricing_markdown,
    pricing_rows,
    trimmed,
)

PRICING_ROW = """
<tr>
  <th scope="row" class="pricing-matrix__label">
    <span class="pricing-matrix__feature">Dividends &amp; splits</span>
    <span class="pricing-matrix__scope">Stocks Global</span>
  </th>
  <td class="pricing-matrix__cell"><span class="pricing-matrix__value pricing-matrix__value--text"> Included </span></td>
  <td class="pricing-matrix__cell"><span class="pricing-matrix__value pricing-matrix__value--yes"></span></td>
  <td class="pricing-matrix__cell"><span class="pricing-matrix__value pricing-matrix__value--no"></span></td>
  <td class="pricing-matrix__cell"><span class="pricing-matrix__value pricing-matrix__value--text">&ndash; Not included</span></td>
  <td class="pricing-matrix__cell"><span class="pricing-matrix__value pricing-matrix__value--text">1 yr</span></td>
</tr>
<tr><th>Feature</th></tr>
"""


def test_openapi_problem_accepts_bundled_spec_only() -> None:
    assert openapi_problem("openapi: 3.1.0\npaths:\n  $ref: '#/components/x'\n") is None
    assert openapi_problem("openapi: 3.1.0\n  $ref: './paths/eod.yaml'\n") == "external references: ./paths/eod.yaml"
    assert openapi_problem("<html>") == "not an OpenAPI 3.1 document"


def test_pricing_rows_read_labels_and_plan_values() -> None:
    assert pricing_rows(PRICING_ROW) == [
        ("Dividends & splits (Stocks Global)", ["Included", "yes", "no", "Not included", "1 yr"])
    ]


def test_pricing_rows_drop_long_tooltips() -> None:
    row = PRICING_ROW.replace("Stocks Global", "x" * 81)
    assert pricing_rows(row)[0][0] == "Dividends & splits"


def test_cell_value_falls_back_to_icon_classes() -> None:
    assert cell_value('<span class="pricing-matrix__value--yes"></span>') == "yes"
    assert cell_value("<span></span>") == ""


def test_pricing_markdown_renders_a_table() -> None:
    lines = pricing_markdown([("Screener", ["no", "no", "yes", "no", "yes"])]).splitlines()
    assert lines[-1] == "| Screener | no | no | yes | no | yes |"
    assert lines[-2] == "|---|---|---|---|---|---|"


def test_trimmed_shortens_lists_and_date_series() -> None:
    series = {f"2026-0{month}-30": {"cash": month} for month in range(1, 5)}
    assert trimmed({"yearly": series, "items": [1, 2, 3, 4], "name": "x"}) == {
        "yearly": {"2026-01-30": {"cash": 1}, "2026-02-30": {"cash": 2}},
        "items": [1, 2, 3],
        "name": "x",
    }


def test_demo_url_appends_the_demo_token() -> None:
    assert (
        demo_url(DemoRequest("eod", "eod/AAPL.US?fmt=json"))
        == "https://eodhd.com/api/eod/AAPL.US?fmt=json&api_token=demo"
    )
    assert demo_url(DemoRequest("splits", "splits/AAPL.US")) == "https://eodhd.com/api/splits/AAPL.US?api_token=demo"
