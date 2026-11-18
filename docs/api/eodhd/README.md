# EODHD API

Local copy of the EODHD API documentation for building the EODHD client.

Source: https://eodhd.com/financial-apis/ (downloaded 2026-11-18). EODHD publishes machine-readable documentation,
so the official files are kept unchanged.

## Files

| File | Content |
|---|---|
| `openapi.yaml` | Official OpenAPI 3.1 specification, bundled into one file (89 paths, parameters, response schemas). The primary reference for the client |
| `llms-full.txt` | Complete reference in Markdown: every endpoint with parameters, call cost, response fields, plans, exchange codes, edge cases ("Edge Cases & Gotchas") |
| `llms.txt` | Short overview: authentication, symbol format, endpoint list with call costs, limits, plans |
| `pricing.md` | Plan comparison table from the pricing page (what each plan includes) |
| `examples/*.json` | Responses of the public `demo` key (AAPL.US, MSFT.US, VTI.US), shortened to the first 3 list items and the first 2 entries of date series |
| `FREE-TIER.md` | What the free plan covers and what it means for the screener |
| `extract_docs.py` | The tool that downloads the files above (see "Regenerating") |

The demo key does not work for search, the screener, the exchange lists and logos (403/401), so there are no
examples for them; their response fields are in `openapi.yaml` and `llms-full.txt`.

## Basics

- Base URL: `https://eodhd.com/api/`
- Authentication: `api_token` query parameter or `Authorization: Bearer <token>` header
- Formats: `fmt=json` (recommended; the default differs per endpoint) or `fmt=csv`
- Symbols: `{TICKER}.{EXCHANGE}` everywhere, e.g. `AAPL.US`, `BMW.XETRA`, `BP.LSE`, `MC.PA`, `ASML.AS`
  (exchange codes in `llms-full.txt`, "Supported Exchanges")
- Every request costs API calls from the daily allowance: 1 for EOD, live quotes (per ticker), dividends, splits,
  search and exchange lists; 5 for the screener; 10 for fundamentals. Failed requests cost nothing.
- Errors: 401 `Unauthenticated` (bad key), 403 `Forbidden` (not in the plan), 402 (daily limit reached),
  429 (minute limit reached), 422 (invalid parameter), `{"error": ..., "code": 404}` (symbol not found)
- Response headers `x-ratelimit-limit` and `x-ratelimit-remaining`; `GET /user` returns the usage (0 calls)

## Endpoints used by the screener

| Feature | Endpoint | Calls | Example |
|---|---|---|---|
| Search (name, ticker, ISIN; returns previous close) | `GET /search/{query}` | 1 | none (no demo) |
| Exchanges (name, code, MIC, country, currency) | `GET /exchanges-list` | 1 | none (no demo) |
| Exchange symbol list (with ISIN, type) | `GET /exchange-symbol-list/{EXCHANGE}` | 1 | none (no demo) |
| Exchange trading hours and holidays | `GET /exchange-details/{EXCHANGE}` | 1 | none (no demo) |
| Delayed quote, batch via `s=` | `GET /real-time/{SYMBOL}?s=...` | 1 per ticker | `real-time-batch.json` |
| US extended quotes (bid/ask, extended hours) | `GET /us-quote-delayed?s=...` | 1 per ticker | `us-quote-delayed.json` |
| End-of-day prices | `GET /eod/{SYMBOL}` | 1 | `eod.json` |
| Fundamentals: profile, ratios, statements (use `filter=`) | `GET /fundamentals/{SYMBOL}` | 10 | `fundamentals-*.json` |
| Dividends | `GET /div/{SYMBOL}` | 1 | `div.json` |
| Upcoming dividends | `GET /calendar/dividends` | 1 | `calendar-dividends.json` |
| Splits | `GET /splits/{SYMBOL}` | 1 | `splits.json` |
| Stock screener | `GET /screener?filters=...` | 5 | none (no demo) |
| Logos (PNG, SVG) | `GET /logo/{SYMBOL}`, `GET /logo-svg/{SYMBOL}` | marketplace | none |

Fundamentals sections: `General` (type, exchange, sector, industry, ISIN, logo URL, primary ticker, listings),
`Highlights` (market cap, P/E, PEG, EPS, margins, ROA, ROE), `Valuation` (trailing and forward P/E, P/S, P/B, EV
ratios), `SharesStats`, `Technicals` (beta, 52-week range), `SplitsDividends`, `Earnings`, `Financials`
(balance sheet, income statement, cash flow, yearly and quarterly).

## Regenerating

`openapi.yaml`, `llms.txt`, `llms-full.txt`, `pricing.md` and `examples/` are generated; `README.md` and
`FREE-TIER.md` are written by hand. To refresh (stdlib only, no API key needed; the examples take 9 requests with
the `demo` key):

```
uv run docs/api/eodhd/extract_docs.py
```

`--skip-examples` keeps the existing demo responses. The demo requests are listed in `DEMO_REQUESTS` in
`extract_docs.py`. Tests:

```
uv run --no-project --python 3.13 --with pytest pytest docs/api/eodhd
```
