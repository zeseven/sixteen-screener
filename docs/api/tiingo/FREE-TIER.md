# Tiingo free tier (Starter plan)

Based on the Tiingo documentation, product and pricing pages (downloaded 2026-11-18), not verified with a real
API token. Sources: `pricing.md`, `pages/general.md` (1.1.3), `products/dividends.md`,
`products/consolidated-realtime-product.md` and `pages/fundamentals.md`.

## Limits

| Limit | Starter (free) | Power ($30/month, $300/year) | Commercial ($50/month) |
|---|---|---|---|
| Requests per hour | 50 | 10,000 | 20,000 |
| Requests per day | 1,000 | 100,000 | 150,000 |
| Bandwidth per month | 1 GB | 40 GB | 100 GB |
| Unique symbols per month | 500 | all | all |
| License | Internal use only | Internal use only | Internal commercial use |

- Hourly requests reset every hour, daily requests at midnight EST, bandwidth on the first of the month.
- There are no per-minute or per-second limits.
- The 500 unique symbols per month count every distinct ticker requested in a calendar month (the beta feature
  "Tiingo monthly limit of unique symbols tracked and respected" in `docs/FEATURES.md`).

## Data on the free plan

| Data | Free plan |
|---|---|
| End-of-day prices (`/tiingo/daily/<ticker>/prices`), full history | Yes |
| Security metadata (`/tiingo/daily/<ticker>`) | Yes |
| Search (`/tiingo/utilities/search`, beta) | Yes |
| Real-time 24x5 consolidated prices (`/tiingo/equity/intraday`) | Yes, included on every plan |
| IEX real-time prices (`/iex`) | Yes |
| News | Yes, last 3 months |
| Fundamentals (statements, daily ratios, meta) | DOW 30 only, 3 years (all tickers need a paid add-on) |
| Dividends and splits (corporate actions, beta) | Unclear, see below |
| Symbol directory (`supported_tickers.zip`) | Yes (file download, no request counted) |

The dividends and splits pages contradict themselves: the banner says the corporate action endpoints are
"available to Beta-enabled customers and enterprise customers" (access by e-mail to support), while the text says
they are "included for all customers with the End-of-Day endpoint entitlement". Needs a check with a real token.
Dividends are also available without that endpoint: the end-of-day prices carry `divCash` on the ex-date.

## Coverage

US stocks, ETFs and mutual funds (about 65,000-80,000 tickers) plus Chinese A-shares. No European or UK
exchanges on any plan.

## What the screener gets on the free plan

| Feature | Free plan |
|---|---|
| Search | Yes (US and China only), beta endpoint |
| Price (watchlist, detailed screen) | Yes; real-time endpoints take many tickers in one request |
| Previous price | Yes, from end-of-day prices |
| Ratios (P/E, P/B, PEG, market cap) | DOW 30 only |
| Balance sheet | DOW 30 only, 3 years |
| Company profile | Partly: name, exchange and description from the EOD metadata; sector, industry and ADR flag from the fundamentals metadata (DOW 30 only on the free plan) |
| Dividends | `divCash` in end-of-day prices; the dividends endpoint may need beta access |
| Stock screener | No such endpoint |
| EU exchanges | No |

## Consequences

- Tiingo is a good free price source for US stocks: 1,000 requests per day, batch real-time quotes and full
  end-of-day history. The 500 unique symbols per month are the main constraint, not the request count.
- It is not a fundamentals source on the free plan (DOW 30 only), so ratios and balance sheets keep coming from
  Finnhub, SEC EDGAR or FMP.
- It does not help the beta goal "wider coverage of European exchanges".
- To verify with a token: the error responses for the hourly/daily limit and the unique-symbol limit, access to the
  corporate action endpoints, and the fundamentals response for a non-DOW-30 ticker.
