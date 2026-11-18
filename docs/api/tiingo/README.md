# Tiingo API

Local copy of the Tiingo API documentation for building the Tiingo client.

Source: https://www.tiingo.com/documentation/general/overview (downloaded 2026-11-18).

## Files

| File | Content |
|---|---|
| `pages/*.md` | API documentation, one file per docs page: endpoints, request and response field tables, example responses |
| `products/*.md` | Product pages (coverage, update times, plan tables per product) |
| `pricing.md` | Pricing comparison (plan limits) and pricing summary |
| `FREE-TIER.md` | What the free plan covers and what it means for the screener |
| `extract_docs.py` | The extraction tool that generates `pages/`, `products/` and `pricing.md` (see "Regenerating") |

The example responses are Tiingo's documented samples, not responses recorded with our API token. Code examples
in Python, Node and PHP are left out; the request URLs are kept. Values the website fills in at runtime (the
user's token, live security counts) appear as `YOUR_API_TOKEN` or `{binding}`.

## Basics

- Base URL: `https://api.tiingo.com/`
- Authentication (`pages/connecting.md`): the `token` query parameter, or the header
  `Authorization: Token <token>`. Test endpoint: `https://api.tiingo.com/api/test/`
- Formats: JSON (default) or CSV (`format=csv`) on most endpoints
- Symbols use `-` instead of `.` (`BRK.A` is `BRK-A`, `pages/symbology.md`)
- Coverage: US stocks, ETFs and mutual funds, Chinese A-shares; no European exchanges

## Endpoints used by the screener

| Feature | Endpoint | Docs |
|---|---|---|
| Search (beta) | `GET /tiingo/utilities/search?query=<query>` (`exactTickerMatch`, `includeDelisted`, `limit` up to 100) | `pages/search.md` |
| Security metadata (name, exchange, description) | `GET /tiingo/daily/<ticker>` | `pages/eod.md` (2.1.3) |
| End-of-day prices (latest or history) | `GET /tiingo/daily/<ticker>/prices` | `pages/eod.md` (2.1.2) |
| Real-time prices (24x5 consolidated), several tickers per request | `GET /tiingo/equity/intraday?tickers=aapl,spy` | `pages/equity-realtime-stock-data.md` |
| Real-time prices (IEX), several tickers per request | `GET /iex/?tickers=aapl,spy` | `pages/iex.md` |
| Fundamentals metadata (sector, industry, ADR flag, ...) | `GET /tiingo/fundamentals/meta?tickers=...` | `pages/fundamentals.md` (2.8.5) |
| Daily fundamentals (market cap, P/E, P/B, PEG) | `GET /tiingo/fundamentals/<ticker>/daily` | `pages/fundamentals.md` (2.8.4) |
| Statements (balance sheet, income, cash flow, overview ratios) | `GET /tiingo/fundamentals/<ticker>/statements` | `pages/fundamentals.md` (2.8.3) |
| Field definitions of the fundamentals | `GET /tiingo/fundamentals/definitions` | `pages/fundamentals.md` (2.8.2) |
| Dividends (beta) | `GET /tiingo/corporate-actions/<ticker>/distributions` | `pages/dividends.md` |
| Dividend yield history (beta) | `GET /tiingo/corporate-actions/<ticker>/distribution-yield` | `pages/dividends.md` |
| Splits (beta) | `GET /tiingo/corporate-actions/<ticker>/splits` | `pages/splits.md` |
| Symbol directory | `https://apimedia.tiingo.com/docs/tiingo/daily/supported_tickers.zip` (daily file, not an API call) | `pages/eod.md` (2.1.3) |

There is no stock screener endpoint in the Tiingo API.

## Regenerating

`pages/`, `products/` and `pricing.md` are generated; `README.md` and `FREE-TIER.md` are written by hand. To
refresh the generated files from the website (stdlib only, no API token needed):

```
uv run docs/api/tiingo/extract_docs.py
```

Tests:

```
uv run --no-project --python 3.13 --with pytest pytest docs/api/tiingo
```
