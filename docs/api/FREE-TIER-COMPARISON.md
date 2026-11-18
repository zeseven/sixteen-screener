# Free tier comparison of the additional sources

Based on each source's documentation (downloaded 2026-11-18), not verified with real API keys. Details per source:
`fmp/FREE-TIER.md`, `tiingo/FREE-TIER.md`, `alphavantage/FREE-TIER.md`, `eodhd/FREE-TIER.md`.

| | FMP | Tiingo | Alpha Vantage | EODHD |
|---|---|---|---|---|
| Requests | 250/day | 1,000/day, 500 symbols/month | 25/day | 20 calls/day (weighted) |
| Search | global | US + China | global | global, probably free (unconfirmed) |
| Prices | 87 sample symbols | all US, real-time, batch | global, end of day | global, 15-min delayed, batch |
| Ratios / profile | 87 sample symbols | Dow 30 only | none | none |
| Balance sheet | 87 sample symbols | Dow 30 only | yes (US at least) | none |
| Dividends | 87 sample symbols | in end-of-day prices | yes | yes, 1 year |
| EU exchanges | paid (Ultimate) | none | London, XETRA | all major ones |
| Screener | paid | none | none | paid (EUR 29.99) |

## Conclusions

- No free plan replaces Finnhub for ratios and company profiles.
- Tiingo is the best free price source for US stocks.
- EODHD and Alpha Vantage are the only free sources of European prices, suitable for explicit requests only.
- For one paid source that covers Europe, EODHD is the best value: EUR 19.99/month for prices,
  EUR 59.99/month with fundamentals.
