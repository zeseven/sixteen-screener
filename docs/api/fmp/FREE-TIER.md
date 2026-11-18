# FMP free tier (Basic plan)

Based on the FMP docs and pricing page (downloaded 2026-11-18), not verified with a real API key.
Plan coverage per endpoint: `endpoints.md`; full plan matrix: `plans.md`.

## Limits

- 250 requests per day (paid plans count per minute instead)
- 500 MB bandwidth in a trailing 30-day window
- Up to 5 years of history; many endpoints return at most 5 records (years, quarters, dividends) per call
- No real-time data: end-of-day only
- Annual data only for ratios and key metrics (no quarters)

## Symbol coverage

Most data endpoints work only for a fixed sample of symbols on the free plan:

- `sample-company`: 87 large US companies (AAPL, TSLA, AMZN, MSFT, NVDA, ...), listed in `plans.md`
- `sample-all`: 207 symbols (the companies above plus crypto, forex, indices, commodities), used by `quote-short`
  and `stock-price-change`
- `us-only`: any symbol on US exchanges (NASDAQ, NYSE, AMEX, CBOE, OTC, PNK, CNQ), used by `profile`

## What the screener gets on the free plan

| Feature | Endpoint | Free plan |
|---|---|---|
| Search | `search-symbol`, `search-name` | Yes, global |
| Company profile | `profile` | US exchanges only |
| Quote | `quote` | 87 sample symbols only |
| Quote (price and change only) | `quote-short` | 207 sample symbols only |
| Ratios | `ratios-ttm`, `key-metrics-ttm`, `financial-scores` | 87 sample symbols only |
| Balance sheet | `balance-sheet-statement` | 87 sample symbols, 5 years |
| Dividends | `dividends` | 87 sample symbols, 5 records |
| Historical prices | `historical-price-eod/light`, `historical-price-eod/full` | 87 sample symbols only |
| Exchange directory | `stock-list`, `available-exchanges`, `search-exchange-variants` | No (Starter and up) |
| Batch quotes | `batch-quote`, `batch-quote-short` | No (Premium and up) |
| Stock screener | `company-screener` | No (Starter: US, Premium: US/UK/Canada, Ultimate: global) |
| EU exchanges | any symbol endpoint | Only on Ultimate (global coverage) |

## Consequences

- With the free plan, FMP can serve search for all markets, profiles for US companies and full data only for
  the sample symbols. It cannot be a general price or ratio source for the watchlist.
- No batch quotes: a watchlist refresh costs one request per symbol, out of 250 per day.
- The beta goal "wider coverage of European exchanges" needs the Ultimate plan with FMP.
- The response for a symbol or endpoint outside the plan is not documented; it has to be checked with a real key
  so that the client can map it to a "not covered by the plan" status instead of an error.

## Paid plans for comparison (personal use)

| Plan | Price | Requests | Coverage |
|---|---|---|---|
| Starter | $29/month ($19 annual) | 300 / minute | US, annual ratios, 5 years |
| Premium | $69/month ($49 annual) | 750 / minute | US, UK, Canada, quarterly data, 30+ years, batch quotes |
| Ultimate | $139/month ($99 annual) | 3000 / minute | Global, bulk endpoints |
