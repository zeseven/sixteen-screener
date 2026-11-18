# EODHD free tier

Based on the EODHD pricing page, `llms.txt` and `llms-full.txt` (downloaded 2026-11-18), not verified with a real
API key. Sources: `pricing.md`, `llms-full.txt` (Rate Limits, Subscription Plans, endpoint sections).

## Limits

- 20 API calls per day (reset at midnight GMT) and 20 requests per minute
- Calls are weighted: 1 for EOD, a delayed quote (per ticker), dividends, splits, search; 10 for fundamentals
- 1 year of history for time series (EOD prices, dividends, splits)
- Failed requests cost nothing; `x-ratelimit-remaining` shows what is left
- Daily limit reached: HTTP 402; not in the plan: HTTP 403

Paid plans have 100,000 calls per day and 1,000 requests per minute: EOD Historical EUR 19.99/month, EOD +
Intraday EUR 29.99/month (adds the screener), Fundamentals EUR 59.99/month, All-In-One EUR 99.99/month.

## Data on the free plan

From the plan comparison table (`pricing.md`):

| Data | Free plan |
|---|---|
| End-of-day prices, global (stocks, ETFs, forex, crypto, indices) | Yes, 1 year |
| Live / delayed quotes, global | Yes, 15-minute delayed |
| US extended quotes | Yes |
| Dividends and splits, global | Yes |
| News and sentiment | Yes |
| Fundamentals (profile, ratios, statements) | No (Fundamentals or All-In-One plan) |
| Logos | No (Fundamentals or All-In-One plan, or the logo marketplace product) |
| Upcoming dividends calendar | Unclear (the corporate calendar row lists earnings, IPOs and splits for Fundamentals and All-In-One) |
| Screener | No (EOD + Intraday or All-In-One) |
| Intraday, technical indicators, WebSocket | No |

Search, the exchange list, the exchange symbol lists and the exchange details are not in the table; they cost
1 call each and are expected to work on the free plan, which has to be checked with a key.

## Coverage

60+ exchanges, including all major European ones: London (`LSE`), XETRA and the German regional exchanges,
Euronext Paris (`PA`), Amsterdam (`AS`), Brussels (`BR`), Lisbon (`LS`), Madrid (`MC`), SIX (`SW`), Vienna (`VI`),
Stockholm (`ST`), Oslo (`OL`), Helsinki (`HE`), Copenhagen (`CO`), Warsaw (`WAR`) and more. Milan (Borsa Italiana)
is not mentioned in the docs; check with the exchange list. Exchange symbol lists include the ISIN.

## What the screener gets on the free plan

| Feature | Free plan |
|---|---|
| Search | Probably yes (1 call), global, ISIN search, results carry the previous close |
| Price (watchlist, detailed screen) | Yes, 15-minute delayed, global, batch via `s=` (1 call per ticker) |
| Previous price | Yes (`previousClose` in the quote) |
| Ratios | No (fundamentals not in the plan) |
| Company profile | No (fundamentals not in the plan) |
| Balance sheet | No (fundamentals not in the plan) |
| Dividends | Yes, last year only |
| Exchange directory | Probably yes (1 call per exchange) |
| Stock screener | No |
| EU exchanges | Yes, for prices and dividends |

## Consequences

- EODHD is the only source with free European quotes and dividends for all symbols, but 20 calls per day allow
  only explicit requests (the RC feature "graceful handling of very low daily limits (EODHD 20/day)").
- A batch quote of 15 symbols costs 15 calls, so the free plan cannot refresh a watchlist.
- No ratios, profiles or balance sheets on the free plan; one fundamentals request would cost 10 of the 20 calls
  even on a plan that includes them.
- The cheapest plan that removes the limit for prices is EOD Historical (EUR 19.99/month, 100,000 calls/day,
  global delayed quotes, 30+ years of history). Fundamentals for European companies need the Fundamentals plan
  (EUR 59.99/month); the docs state international fundamentals from 2000.
- To verify with a key: whether search, the exchange lists and exchange details work on the free plan, and
  how the 1-year history limit affects dividends.
