# Side-by-side source comparison: UX investigation

Investigation of how the detailed screen could compare data of several sources (ADR-007, future improvement
"Side-by-side comparison of several sources on the detailed screen"). Nothing here is implemented in alpha; the alpha
detailed screen shows one source at a time, with a source dropdown on the balance sheet tab.

## What is compared

| Tab | Shape of the data | Comparison need |
|-----|-------------------|-----------------|
| Price and ratio | One price plus a flat list of 10-30 named ratios | Same ratio from 2-5 sources; differences are often small (rounding, different period ends) |
| Balance sheet | Line items x fiscal years (ADR-053), already a wide table | Same line item and year from a market data source and the "source of truth" (SEC EDGAR); labels and concepts differ between sources |
| Dividends (beta) | Dated list of payments | Missing or extra payments, amount differences |
| Company profile | Short text fields | Rarely interesting; differences are mostly formatting |

Sources expected by RC: Finnhub, FMP, Tiingo, Alpha Vantage, EODHD and SEC EDGAR (balance sheet only), so up to 5 sources
for prices and ratios and 6 for the balance sheet. Each source has its own request limits and cache, so a comparison
view must work with partially loaded data: some sources cached, some loading, some at their limit or not supporting
the company.

## Phone width constraints

- Typical portrait width is 360-412 dp; minus 16 dp margins on each side about 330-380 dp is usable.
- A readable label column needs 120-150 dp (ratio names like "Debt/Equity (annual)" or balance sheet labels like
  "Accumulated other comprehensive income"). That leaves about 200-250 dp for values.
- An abbreviated value (`352.6 B`, `-1.5 M`, `31.25`) needs 64-80 dp at body-small size. So 3 value columns fit next to
  the label column; 4 fit only with a narrower label column and smaller type; 5-6 sources never fit without horizontal
  scrolling.
- The balance sheet already scrolls horizontally over years. Adding sources as a second horizontal dimension creates a
  three-dimensional table (item x year x source) that can't be shown on a phone without dropping one dimension.
- Landscape and large-screen layouts (foldables, tablets) give 600-900 dp, where 5-6 source columns fit.
- With large font scale (accessibility settings) the number of columns that fit halves; any layout must degrade to
  scrolling rather than truncating values.

## Option A: one row per value, one column per source

```
 Ratio            Finnhub   FMP      Tiingo
 P/E              31.25     31.20    31.3
 P/B              48.1      48.1     -
 ROE              1.56      1.47 *   1.56
 Debt/Equity      1.87      1.87     1.87
```

The label column stays fixed; source columns scroll horizontally when they don't fit (the same pattern as the alpha
balance sheet table). A source picker (chips) above the table selects which sources are shown; the default source is
always the first column.

- Pros: all values of a row visible at once, differences are obvious by scanning a row; reuses the existing table
  component; natural for ratios, which are one value per source.
- Cons: for the balance sheet a year has to be chosen first (year dropdown or year chips), because years and sources
  can't both be columns; more than 3 sources need horizontal scrolling, and then the compared values are not all
  visible together.
- Status per source (limit reached, not supported, loading, data age) goes into the column header as a small icon with a
  content description; tapping the header shows the full status.

## Option B: swipe between sources

The current single-source tab layout stays; a horizontal pager switches between sources (page indicator and source name
in the header), with the default source as the first page. Rows keep the same vertical position on every page, so
swiping back and forth shows which values change.

- Pros: no width problem at all, every page has the full width; the balance sheet keeps its year columns; small change
  from the alpha UI (the dropdown becomes a pager); each page can show its own status notice exactly as today.
- Cons: values are never visible together, so comparing relies on memory or on flipping pages; differences in one row
  out of 30 are easy to miss; the horizontal swipe conflicts with the horizontally scrolling balance sheet table (the
  pager can only be switched via the header or tabs there).

## Option C: default source with difference highlighting

The tab shows the default source as today. Values where other sources differ by more than a threshold (for example
more than 1 % relative, or a value missing in one source) get a marker: a small dot plus a text suffix such as
`31.25 (2 differ)`, so color is not the only signal. Tapping a row expands it in place (or opens a bottom sheet) with
the value of every source, its data age and status.

```
 P/E              31.25
 ROE              1.56   2 differ  v
    Finnhub       1.56   updated 2 hours ago
    FMP           1.47   updated 1 day ago
    Tiingo        1.56   cached, limit reached
 Debt/Equity      1.87
```

- Pros: fits any width and any number of sources; the screen stays as calm as the single-source view when sources
  agree, which is the common case; works for the balance sheet per cell (expand a cell to see all sources for that
  item and year); the comparison logic (threshold, matching by concept) is a pure domain function that is easy to test.
- Cons: requires data from all sources before the markers are complete, which costs source requests (each source
  respects its own limits and cache, beta feature "Data gathered from all sources for comparison"); the threshold must
  be chosen per kind of value (a 1 % difference matters for a ratio, not for a rounded share count); matching balance
  sheet rows across sources needs a concept mapping because sources label items differently.

## Comparison

| Criterion | A: columns per source | B: swipe | C: highlight differences |
|-----------|-----------------------|----------|--------------------------|
| Values visible together | Yes, up to 3 sources | No | On demand per row |
| Fits 360 dp with 5-6 sources | No (horizontal scroll) | Yes | Yes |
| Balance sheet (item x year) | Needs a year selector | Unchanged | Per cell |
| Request cost | All shown sources | One source per page, on demand | All sources for markers |
| Implementation effort | Low (table exists) | Low | Medium (diff logic, expandable rows) |
| Accessibility | Table with headers; long reading order | Simple pages | One item per row, expanded detail read as a group |

## Recommendation

Use **Option C** as the main pattern on phones: the default source stays the primary view, and differences are marked
in place and expanded on tap. It scales to all sources planned for RC, keeps the balance sheet table as it is, and
shows something useful even when only some sources have cached data (rows without a comparison yet are simply not
marked).

Use **Option A** in two cases: for the price and ratio tab when 3 or fewer sources are selected, and as the layout
for wide windows (width class medium or expanded: landscape, foldables, tablets), where 5-6 source columns fit.

Option B is not recommended: it hides exactly the information the comparison is for, and it conflicts with the
horizontal scrolling of the balance sheet.

Suggested order for beta:
1. Domain function comparing a value across sources (relative threshold per value kind, missing values counted as a
   difference) with unit tests.
2. Difference markers and expandable rows on the price and ratio tab, using cached data only, so no extra requests are
   spent until the user asks for a refresh.
3. Balance sheet cells, after a concept mapping between SEC EDGAR and the market data sources is available.
4. Column layout for wide windows.

Open questions for the developer:
- Should opening the detailed screen fetch all sources (more requests, complete markers) or only cached data plus the
  default source (cheaper, markers may be incomplete)?
- Threshold values per ratio, or one global threshold in settings?
