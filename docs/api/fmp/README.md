# Financial Modeling Prep (FMP) - stable API

Local copy of the FMP API documentation for building the FMP client. Only the `stable` API is
covered; the legacy `/api/v3` and `/api/v4` endpoints are not used.

Source: https://site.financialmodelingprep.com/developer/docs (downloaded 2026-11-18).

## Files

| File | Content |
|---|---|
| `endpoints.md` | Endpoints used by the screener (all milestones in `docs/FEATURES.md` plus the stock screener): request, parameters, plan coverage, response fields |
| `examples/<doc id>.json` | Example responses as published in the FMP docs (one per endpoint in `endpoints.md`) |
| `plans.md` | Plan matrix from the pricing page for every data set, plus the sample symbol and exchange sets the coverage tables refer to |
| `catalog.json` | All 276 `stable` endpoints (id, category, path, parameters, plan coverage, docs link), for endpoints added later |
| `FREE-TIER.md` | What the free plan covers and what it means for the screener |
| `extract_docs.py` | The extraction tool that generates the files above (see "Regenerating") |

The example responses are FMP's documented samples, not responses recorded with our API key.
Field types in `endpoints.md` are derived from those samples; nullability is not documented.

## Basics

- Base URL: `https://financialmodelingprep.com/stable/`
- Authentication: API key as the `apikey` query parameter on every request,
  e.g. `https://financialmodelingprep.com/stable/search-symbol?query=AAPL&apikey=KEY`
- Responses are JSON arrays, also for single-record endpoints (`profile`, `quote`, `ratios-ttm`, ...).
- Lists of symbols in batch endpoints are comma-separated (`symbols=AAPL,MSFT`).

Documented error codes:

| Code | Meaning |
|---|---|
| 401 | Invalid or missing API key (body: `{"Error Message": "Invalid API KEY. ..."}`) |
| 429 | Too many requests (rate limit exceeded) |
| 500 | Internal server error |

The response for a symbol or endpoint outside the plan coverage is not documented; it has to be
checked with a real key.

## Plans and limits (personal use)

| Plan | Code in coverage tables | Price | Requests | Bandwidth (trailing 30 days) | History | Coverage |
|---|---|---|---|---|---|---|
| Basic (free) | `free` | $0 | 250 / day | 500 MB | 5 years | Samples, US for some endpoints |
| Starter | `IND1` | $29/month ($19 annual) | 300 / minute | 20 GB | 5 years | US |
| Premium | `IND2` | $69/month ($49 annual) | 750 / minute | 50 GB | 30+ years | US, UK, Canada |
| Ultimate | `IND3` | $139/month ($99 annual) | 3000 / minute | 150 GB | full | Global |

`COM1` and `COM3` are commercial plans. In `endpoints.md` and `catalog.json`, "Available only on"
lists the plans that include an endpoint; the other rows list per-plan restrictions:

- `symbol = sample-company`: only 87 sample symbols (AAPL, TSLA, AMZN, MSFT, ...; listed in `plans.md`)
- `symbol = sample-all`: 207 sample symbols (companies, crypto, forex, indices, commodities)
- `symbol = us-only`: US exchanges (NASDAQ, NYSE, AMEX, CBOE, OTC, PNK, CNQ)
- `symbol = us-uk-can`: US, UK and Canada exchanges (adds NEO, TSXV, TSX, LSE)
- `limit = 5`: at most 5 records (years or quarters) per call
- `period = annual_only`: no quarterly data

What the free plan means for the screener: `FREE-TIER.md`.

## Regenerating

`endpoints.md`, `examples/`, `plans.md` and `catalog.json` are generated; `README.md` and `FREE-TIER.md` are
written by hand. To refresh the generated files from the docs site (stdlib only, no API key needed):

```
uv run docs/api/fmp/extract_docs.py
```

The endpoints included in `endpoints.md` are listed in `SECTIONS` in `extract_docs.py`. Tests:

```
uv run --no-project --python 3.13 --with pytest pytest docs/api/fmp
```
