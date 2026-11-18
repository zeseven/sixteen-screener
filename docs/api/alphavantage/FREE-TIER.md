# Alpha Vantage free tier

Based on the Alpha Vantage documentation, premium and support pages (downloaded 2026-11-18), not verified with a
real API key. Sources: `plans.md`, `endpoints.md`, `catalog.json`.

## Limits

- 25 requests per day (unlimited for verified open-source or educational projects)
- No per-minute limit is documented for the free plan
- Premium functions (labelled `Premium` in `endpoints.md`) are not available
- Quotes are end of day: realtime and 15-minute delayed US data need a premium plan
- Daily prices: only the latest 100 data points (`outputsize=compact`); full history is premium

Premium plans start at $49.99/month for 75 requests per minute with no daily limit (up to 1200 requests per
minute for $249.99/month).

## What the screener gets on the free plan

| Feature | Function | Free plan |
|---|---|---|
| Search | `SYMBOL_SEARCH` | Yes, global (US, UK, Germany, Canada, India, China, ...) |
| Quote | `GLOBAL_QUOTE` | Yes, end of day, one symbol per request, global |
| Previous price | `GLOBAL_QUOTE` (`08. previous close`) | Yes |
| Batch quotes | `REALTIME_BULK_QUOTES` | No (premium, US only) |
| Ratios (P/E, PEG, ROA, ROE, margins, beta, ...) | `OVERVIEW` | No (premium) |
| Company profile (sector, industry, description, asset type) | `OVERVIEW` | No (premium) |
| Logo (PNG and SVG) | `COMPANY_LOGO` | Yes |
| Balance sheet (annual and quarterly) | `BALANCE_SHEET` | Yes |
| Income statement, cash flow, EPS | `INCOME_STATEMENT`, `CASH_FLOW`, `EARNINGS` | Yes |
| Dividends (history and declared) | `DIVIDENDS` | Yes |
| Symbol directory | `LISTING_STATUS` | Yes, US stocks and ETFs only (one CSV per request) |
| Stock screener | none | No such function |

## Coverage

- Prices and search: global. The docs give examples for London (`.LON`), XETRA (`.DEX`), Toronto (`.TRT`, `.TRV`),
  India (`.BSE`) and China (`.SHH`, `.SHZ`). Other European exchanges (Paris, Amsterdam, Milan, ...) are not
  mentioned in the docs and have to be checked with search.
- Fundamentals: the statements are "mapped to GAAP and IFRS taxonomies of the SEC", all examples use US companies.
  Coverage of non-US companies is not documented.

## Consequences

- Alpha Vantage is the first source with European prices on a free plan (at least London and XETRA), but at
  25 requests per day it can only serve prices on explicit demand, not a watchlist refresh.
- Without `OVERVIEW` (labelled Premium in the current docs) it gives no ratios and no sector on the free plan. Ratios
  could be calculated from the statements (EPS, book value, ROE, ROA), but each statement costs a request.
- The balance sheet, dividends and logo functions are free and useful as additional sources on the detailed
  screen, each costing one request per symbol.
- The RC feature "graceful handling of very low daily limits (Alpha Vantage 25/day)" in `docs/FEATURES.md` is
  the right design: requests only on explicit demand, results cached.
- To verify with a key: which European suffixes search returns, whether the statements and dividends cover
  European companies, and the exact limit and premium responses (`Information` or `Note` field).
