# Sixteen Screener Yahoo proxy

Python service that reads Yahoo Finance data with the community library [`yfinance`](https://github.com/ranaroussi/yfinance)
and serves it as JSON to the backend. Yahoo has no official API; `yfinance` follows the changes of the Yahoo web
endpoints, so most Yahoo breakages are fixed by upgrading `yfinance` and redeploying this service, without changes to
the backend.

The proxy is stateless and works on demand: no cache, no database, no scheduler. Caching, request limits and counters
are kept by the backend, which is the only caller. The service runs on Cloud Run with min instances 0 and max
instances 1.

One-time configuration of the GCP project (service account, IAM) is described in `SETUP.md`. All commands below are
run from the `yahoo-proxy` folder unless stated otherwise.

## Endpoints

All endpoints are `GET`, take a Yahoo symbol (`AAPL`, `BRK-B`, `SAP.DE`, `SHEL.L`) and return JSON with camelCase
fields. Symbols are upper case letters, digits, `.` and `-`, at most 25 characters; anything else is rejected with
`400` without calling Yahoo. Every endpoint makes one or two Yahoo requests (see Pacing).

| Path                               | Data                                                                     |
|------------------------------------|--------------------------------------------------------------------------|
| `/health`                          | `{"status":"ok"}` without calling Yahoo; used by the backend to warm up  |
| `/symbols/{symbol}/quote`          | latest price, previous close, currency, market time, exchange            |
| `/symbols/{symbol}/statistics`     | key statistics: ratios and per share amounts                             |
| `/symbols/{symbol}/profile`        | company profile                                                          |
| `/symbols/{symbol}/dividends`      | full dividend history, oldest first                                      |
| `/symbols/{symbol}/balance-sheet`  | annual balance sheets, newest first                                      |

### Currencies

Yahoo quotes some exchanges in minor units: `GBp` (London), `ZAc` (Johannesburg), `ILA` (Tel Aviv). The proxy converts
prices and dividends into the main unit (`GBP`, `ZAR`, `ILS`, divided by 100) and returns the main unit code. Every
amount comes with its currency:

- quote and dividends: `currency`, the currency of the quote;
- statistics: `currency` for `amounts`, the main unit of the quote currency (Yahoo reports these amounts in the main
  unit already, e.g. EPS of `SHEL.L` in GBP); `null` and no amounts when Yahoo gives no currency;
- profile: `currency` (quote) and `financialCurrency` (financial statements);
- balance sheet: `currency`, the currency of the financial statements (`USD` for `SHEL.L`).

### Contract

`/symbols/SHEL.L/quote`:

```json
{"symbol":"SHEL.L","price":36.185,"previousClose":35.745,"currency":"GBP","marketTime":"2026-11-26T14:33:34Z","exchange":"LSE"}
```

`previousClose` is the close of the day before the latest trading day; `marketTime` (UTC) and `exchange` may be `null`.

`/symbols/AAPL/statistics`:

```json
{"symbol":"AAPL","currency":"USD","ratios":{"trailingPE":39.59,"returnOnEquity":1.42,"dividendYield":0.3},"amounts":{"trailingEps":8.98,"marketCap":5167353167872.0}}
```

Only keys with a value are present. Ratios keep Yahoo's units, which differ per key:

| Unit                 | Keys                                                                                                   |
|----------------------|--------------------------------------------------------------------------------------------------------|
| multiple (`12.3`)    | `trailingPE`, `forwardPE`, `trailingPegRatio`, `priceToBook`, `priceToSalesTrailing12Months`, `enterpriseToRevenue`, `enterpriseToEbitda`, `currentRatio`, `quickRatio`, `beta` |
| fraction (`0.15`)    | `returnOnEquity`, `returnOnAssets`, `grossMargins`, `operatingMargins`, `ebitdaMargins`, `profitMargins`, `revenueGrowth`, `earningsGrowth`, `earningsQuarterlyGrowth`, `payoutRatio` |
| percent (`15`)       | `dividendYield`, `fiveYearAvgDividendYield`, `debtToEquity`                                            |

Amounts (in `currency`): `trailingEps`, `forwardEps`, `bookValue`, `revenuePerShare`, `totalCashPerShare`,
`dividendRate`, `marketCap`. Yahoo fields with mixed or unclear currencies (`trailingAnnualDividendRate`,
`enterpriseValue`, 52-week range) are left out on purpose.

`/symbols/SAP.DE/profile`:

```json
{"symbol":"SAP.DE","name":"SAP SE","quoteType":"EQUITY","typeDisplay":"Equity","exchange":"XETRA","currency":"EUR","financialCurrency":"EUR","sector":"Technology","industry":"Software - Application","country":"Germany","city":"Walldorf","website":"https://www.sap.com","description":"SAP SE, together with its subsidiaries, ...","employees":113076}
```

Every field except `symbol` may be `null`.

`/symbols/SAP.DE/dividends`:

```json
{"symbol":"SAP.DE","currency":"EUR","dividends":[{"exDate":"2025-05-14","amount":2.35},{"exDate":"2026-05-06","amount":2.5}]}
```

`exDate` is the ex-dividend date in the exchange's time zone. A company without dividends returns an empty list
(`200`); `currency` is then `null`.

`/symbols/SAP.DE/balance-sheet`:

```json
{"symbol":"SAP.DE","currency":"EUR","years":[{"fiscalYearEnd":"2025-12-31","items":{"totalAssets":70362000000.0,"stockholdersEquity":44586000000.0}}]}
```

Item keys are the Yahoo item names in camelCase (`Total Assets` is `totalAssets`, `Net PPE` is `netPPE`). Items
without a value are left out; years without any value are left out.

### Errors

Every response outside 2XX has the body `{"error": "<code>", "message": "<text>"}`:

| Status | `error`            | Meaning                                                                              |
|--------|--------------------|--------------------------------------------------------------------------------------|
| `400`  | `INVALID_SYMBOL`   | the symbol doesn't look like a Yahoo symbol; Yahoo was not called                    |
| `404`  | `NOT_FOUND`        | Yahoo has no data of this type for the symbol                                        |
| `429`  | `RATE_LIMITED`     | Yahoo rate-limited the proxy (including a failed cookie or crumb request)            |
| `502`  | `UPSTREAM_FAILURE` | any other Yahoo failure, or a Yahoo result of an unexpected shape (logged with trace) |
| `504`  | `TIMEOUT`          | Yahoo did not answer within `YAHOO_TIMEOUT_SECONDS`                                  |

## Configuration

| Variable                          | Default            | Description                                                                 |
|-----------------------------------|--------------------|-----------------------------------------------------------------------------|
| `PORT`                            | `8080`             | HTTP port (set by Cloud Run)                                                |
| `YAHOO_TIMEOUT_SECONDS`           | `25`               | Time limit of a request: its Yahoo reads and their waits for slot and turn  |
| `YAHOO_MAX_CONCURRENT_CALLS`      | `2`                | Yahoo reads running at the same time; further requests wait                 |
| `YAHOO_MIN_CALL_INTERVAL_SECONDS` | `1`                | Minimum time between the starts of two Yahoo reads; must be above 0         |
| `YFINANCE_CACHE_DIR`              | `$TMPDIR/yfinance` | Cache of exchange time zones kept by `yfinance` (lost on restart)           |

The service validates the configuration on start and exits with a list of all problems found. Logs are written to
stdout as JSON in the Cloud Logging format. The backend's timeout for proxy calls must cover a cold start plus
`YAHOO_TIMEOUT_SECONDS` (40 seconds or more).

### Pacing

The proxy starts at most one Yahoo read per `YAHOO_MIN_CALL_INTERVAL_SECONDS`, in the order the requests arrive. As
the service runs with max instances 1, this is a global limit, whatever the number of backend instances.

- The balance sheet needs two Yahoo reads (the balance sheet, then the quote summary for the financial currency, only
  when there is a balance sheet); each is paced separately. Every other endpoint is one read.
- `YAHOO_TIMEOUT_SECONDS` limits the whole request: all its Yahoo reads and the waits for a free slot and for their
  turns together. A slow or long queued request ends in `504 TIMEOUT`.
- The cookie and crumb requests `yfinance` makes on its own are part of the read that triggers them and are not paced
  separately.
- `GET /health` is never paced.

## Prerequisites

- [`uv`](https://docs.astral.sh/uv/) (installs Python 3.14 from `.python-version` when it is missing)
- Docker (only for local image builds)
- `gcloud` CLI (for building and deploying)

## Run locally

```bash
uv sync
uv run python -m yahoo_proxy
```

The service listens on `http://localhost:8080` and calls the real Yahoo:

```bash
curl -s http://localhost:8080/health
curl -s http://localhost:8080/symbols/SAP.DE/quote
curl -s http://localhost:8080/symbols/SHEL.L/dividends
```

## Checks and tests

```bash
uv run poe lint_full
```

runs `ruff format --check`, `ruff check`, `basedpyright` (strict) and `pytest`. Single steps: `uv run poe format`,
`uv run poe lint`, `uv run poe typecheck`, `uv run poe test`.

Tests never call Yahoo. They replay real `yfinance` results recorded in `tests/fixtures/` (`AAPL`, `SAP.DE`, `SHEL.L`)
as the pandas objects `yfinance` returns. To record them again from the current Yahoo data:

```bash
uv run python scripts/record_fixtures.py AAPL SAP.DE SHEL.L
uv run poe test
```

Values in the tests that depend on the recorded day (prices, dates) have to be adjusted after recording.

## Upgrade yfinance

Do this when the Yahoo source shows repeated errors in the app's connection indicator (`429`, `502`, `504`), or when
a new `yfinance` release is out ([releases](https://github.com/ranaroussi/yfinance/releases)).

```bash
uv lock --upgrade-package yfinance
uv sync
uv run python scripts/record_fixtures.py AAPL SAP.DE SHEL.L
git diff --stat tests/fixtures
uv run poe lint_full
```

`yfinance` is pinned to an exact version in `pyproject.toml`; update the pin to the new version as well. Compare the
recorded fixtures before and after the upgrade: renamed keys or changed units (e.g. a yield as a fraction instead of a
percent) need a change in `src/yahoo_proxy/mapping.py` and in this README, so that the JSON contract stays the same
for the backend. Then build and deploy the new version as below.

## Build a Docker image locally

```bash
docker build -t sixteen-screener-yahoo .
docker run --rm -p 8080:8080 sixteen-screener-yahoo
```

The image runs as a non-root user. It is built for the host architecture; Cloud Run needs `linux/amd64`, so on Apple
Silicon build it with Cloud Build (below).

## Build with Cloud Build

Set the variables from `SETUP.md` section 2, then run from the repository root:

```bash
TAG="$(git rev-parse --short HEAD)"

gcloud builds submit yahoo-proxy \
  --config=yahoo-proxy/cloudbuild.yaml \
  --service-account="projects/${PROJECT_ID}/serviceAccounts/${BUILD_SA}@${PROJECT_ID}.iam.gserviceaccount.com" \
  --substitutions=_REGION="$REGION",_AR_REPOSITORY="$AR_REPOSITORY",_SERVICE_NAME="$SERVICE_NAME",_TAG="$TAG"
```

The build runs the `Dockerfile` on the default Cloud Build machine and pushes the image to the backend's Artifact
Registry repository. Files listed in `.gcloudignore` (tests, scripts, local environment) are not uploaded.

## Deploy to Cloud Run

```bash
gcloud run deploy "$SERVICE_NAME" \
  --region="$REGION" \
  --image="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPOSITORY}/${SERVICE_NAME}:${TAG}" \
  --service-account="${RUNTIME_SA}@${PROJECT_ID}.iam.gserviceaccount.com" \
  --no-allow-unauthenticated \
  --min-instances=0 \
  --max-instances=1 \
  --cpu-boost \
  --cpu=1 \
  --memory=512Mi \
  --concurrency=10 \
  --timeout=60
```

- `--no-allow-unauthenticated`: only principals with `roles/run.invoker` can call the service; that is the backend's
  runtime service account (`SETUP.md` section 5)
- `--min-instances=0`: the service scales to zero when idle; a cold start takes a few seconds, mostly loading pandas
- `--max-instances=1`: one instance keeps one Yahoo session and a predictable request rate to Yahoo, and caps the cost
- `--cpu-boost`: extra CPU during startup, which shortens the cold start
- `--concurrency=10`: matches the backend's limit of 10 requests at a time per source; Yahoo itself is called by at
  most `YAHOO_MAX_CONCURRENT_CALLS` of them at once
- `--timeout=60`: longer than `YAHOO_TIMEOUT_SECONDS`, so a slow Yahoo answers `504` from the proxy rather than a
  Cloud Run timeout
- `--service-account`: runtime service account without any roles

The service URL, which the backend needs as `YAHOO_PROXY_URL`:

```bash
gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)'
```

## Call the deployed service

Only the backend's service account can call the service. For a manual check, grant yourself temporary access as
described in `SETUP.md` section 6, then:

```bash
SERVICE_URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"
TOKEN="$(gcloud auth print-identity-token)"

curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/health"
curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/symbols/SAP.DE/quote"
curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/symbols/SHEL.L/dividends"
```

Every symbol call makes real Yahoo requests from the Cloud Run egress addresses; keep manual checks few.
