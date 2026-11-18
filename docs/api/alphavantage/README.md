# Alpha Vantage API

Local copy of the Alpha Vantage API documentation for building the Alpha Vantage client.

Source: https://www.alphavantage.co/documentation/ (downloaded 2026-11-18).

## Files

| File | Content |
|---|---|
| `endpoints.md` | API functions used by the screener: labels (Premium, Trending, Utility), parameters, examples, notes, response fields |
| `examples/<docs anchor>.json` | Responses of the documented example URLs (public `demo` key), shortened to the first 2 list items and the first 3 entries of date series; `listing-status.csv` keeps the first 20 lines |
| `plans.md` | Free usage limit, premium plans and the realtime data policy from the premium and support pages |
| `catalog.json` | All 135 documented API functions (category, labels, parameters, examples, notes) for functions added later |
| `FREE-TIER.md` | What the free plan covers and what it means for the screener |
| `extract_docs.py` | The extraction tool that generates the files above (see "Regenerating") |

The demo responses are real data for the example symbols (mostly IBM), not responses recorded with our key.
The demo response of `REALTIME_BULK_QUOTES` is an artificial schema sample (it says so in its `message`).

## Basics

- One endpoint for everything: `GET https://www.alphavantage.co/query?function=<FUNCTION>&...&apikey=<key>`
- Authentication: the `apikey` query parameter
- Formats: JSON (default) or CSV (`datatype=csv`) on most functions; `LISTING_STATUS` returns CSV only
- Symbols outside the US carry an exchange suffix: `TSCO.LON` (London), `MBG.DEX` (XETRA), `SHOP.TRT`
  (Toronto), `GPV.TRV` (TSX Venture), `RELIANCE.BSE` (India), `600104.SHH` (Shanghai), `000002.SHZ` (Shenzhen).
  The search endpoint returns the suffixed symbol together with region, currency, market hours and time zone.
- Numbers are returned as strings (`"231.0900"`), missing values as the string `"None"`.
- Several functions use numbered keys (`"05. price"`, `"1. symbol"`).
- Error and limit responses are not documented. Alpha Vantage is known to answer them with HTTP 200 and a single
  `Information`, `Note` or `Error Message` field; to be confirmed with a key.

## Functions used by the screener

| Feature | Function | Free |
|---|---|---|
| Search | `SYMBOL_SEARCH` | yes |
| Symbol directory (US stocks and ETFs, CSV) | `LISTING_STATUS` | yes |
| Market status (open/close by region) | `MARKET_STATUS` | yes |
| Quote (end of day on the free plan) | `GLOBAL_QUOTE` | yes |
| Bulk realtime quotes (up to 100 symbols, US) | `REALTIME_BULK_QUOTES` | no (premium) |
| Daily prices (latest 100 days, full history premium) | `TIME_SERIES_DAILY` | yes |
| Daily adjusted prices with split and dividend events | `TIME_SERIES_DAILY_ADJUSTED` | no (premium) |
| Company profile and ratios | `OVERVIEW` | no (premium) |
| ETF profile | `ETF_PROFILE` | yes |
| Logo (PNG and SVG URLs) | `COMPANY_LOGO` | yes |
| Shares outstanding | `SHARES_OUTSTANDING` | yes |
| Balance sheet, income statement, cash flow | `BALANCE_SHEET`, `INCOME_STATEMENT`, `CASH_FLOW` | yes |
| Earnings (EPS) | `EARNINGS` | yes |
| Dividends, splits | `DIVIDENDS`, `SPLITS` | yes |

There is no stock screener function.

## Regenerating

`endpoints.md`, `examples/`, `plans.md` and `catalog.json` are generated; `README.md` and `FREE-TIER.md` are written
by hand. To refresh the generated files (stdlib only, no API key needed; the demo responses take about 17 requests
to the API with the `demo` key, 2 seconds apart):

```
uv run docs/api/alphavantage/extract_docs.py
```

`--skip-examples` keeps the existing demo responses and only regenerates the Markdown and catalog. The functions
included in `endpoints.md` are listed in `SECTIONS` in `extract_docs.py`. Tests:

```
uv run --no-project --python 3.13 --with pytest pytest docs/api/alphavantage
```
