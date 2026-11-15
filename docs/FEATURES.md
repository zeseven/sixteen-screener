# Features

Features are grouped by milestone. Each milestone produces a working, deployable solution
(backend + Android app). A feature is checked once it is implemented and covered by tests.

- **Alpha** - end-to-end working app with a minimal set of sources: watchlist, search, detailed screen, profiles.
- **Beta** - multiple price/ratio sources, source switching and comparison, indicators, dividends, tags.
- **RC** - all sources, settings completeness, robustness, polishing and release readiness.

---

## Alpha

### Project and infrastructure
- [x] Repo structure: `backend`, `frontend`, `docs`
- [x] OpenAPI spec `docs/openapi.yaml` (all milestones, marked with `x-milestone`) used by both backend and frontend; alpha endpoints implemented
- [x] Backend: Kotlin + Gradle project with hexagonal architecture (domain, ports, adapters)
- [x] Backend: Ktor application compiled into a GraalVM native image and packaged as a Docker image
- [ ] Backend: Cloud Run deployment via gcloud CLI (min instances = 0, startup CPU boost)
- [ ] Backend: endpoint access limited to the frontend app's service account and the developer's Google account (Cloud Run IAM)
- [ ] Backend: Cloud Run max instances cap
- [ ] Backend: Secret Manager integration for source API tokens
- [x] Backend: Firestore integration with TTL for cached data
- [x] Backend: GCS bucket for logos
- [x] Backend: sources list and per-source client configuration via application properties
- [x] `backend/README.md` (build + deploy) and `backend/SETUP.md` (Firestore TTL policy, bucket, secrets, service accounts, IAM)
- [x] Frontend: Kotlin Android project with Jetpack Compose and Material 3 (min Android 8.0 / API 26, dynamic colors on Android 12+)
- [x] Frontend: generated (or spec-aligned) API client and authenticated calls to the backend (ID token minted from the service account key)
- [x] Frontend: service account key injected into the APK at build time from a location outside the repo; build fails if not configured
- [x] `.gitignore` patterns preventing key files from being committed
- [x] `frontend/README.md` (CLI build of APK for USB install) and `frontend/SETUP.md` (service account creation, run.invoker binding, key creation, key location setup, key rotation)

### Sources
- [x] Logo source (logoapi.sh): download SVG logo, store in GCS bucket, serve from bucket on next requests
- [x] Logos served as SVG with a long-lived Cache-Control header (no conversion on the backend)
- [x] Logo rendering in the app with Coil and coil-svg; default logo shown when decoding fails
- [x] Default logo returned when the logo is not found in the bucket nor in the source
- [x] Finnhub client (free plan): symbol search, quote (price), basic financials (ratios), company profile, balance sheet from reported financials (raw, as filed with SEC EDGAR)
- [x] Finnhub used as the default source
- [x] Exchange directory: symbol lists of the source's exchanges downloaded once per day, kept in the GCS bucket and used to resolve exchange and market of search results
- [x] SEC EDGAR client: CIK lookup by ticker, company facts, balance sheet ("source of truth" for US stocks)
- [x] Per-source request counters (minute/hour/day/month) stored in Firestore and shared between instances
- [x] Rate limiting: no outgoing request when a source limit is reached; cached data is returned instead

### Caching and data freshness
- [x] Company data cached per ticker and date, shared between users; expires via Firestore TTL after 14 days
- [x] Data younger than 1 hour is considered fresh and not refreshed
- [x] Company profiles cached for 90 days
- [x] Exchange symbol lists kept for 7 days; a missing symbol triggers a new download of a list older than 1 day
- [x] Stale data refreshed for the watchlist screen (if source limits allow)
- [x] Detailed screen shows cached data on open; refresh only on manual request
- [x] Detailed screen data requested from the source automatically when nothing is cached
- [x] Source error, timeout or reached limit: cached data returned with the source status; without cached data, empty data shown as '-'
- [x] Previous cached price (latest price from yesterday or earlier) available for price coloring
- [x] Cache age returned with the data (for the cache indicator)

### Profiles
- [x] On first start, the app asks for a profile name, gets the profile ID from the backend (get-or-create by name) and stores both locally
- [x] Profile ID used for all per-user requests on next runs without asking again
- [x] Per-profile data stored in Firestore (profile with unique name and backend-generated ID, watchlist)

### Watchlist screen
- [x] List of companies in the profile's watchlist
- [x] Add stock (via search) and remove stock
- [x] Open detailed screen for a company
- [x] Refresh of stale data when the screen is opened
- [x] Only displayed rows (plus a 5-row buffer) refreshed: on screen open, on scrolling new rows into view, on filter change
- [x] Watchlist stored as entries `{symbol, sector, tags}` and loaded without source requests; sector stored on add and updated by the detailed screen
- [x] Prices and indicators lazy-loaded per displayed row; '-' until loaded; orange after a failed load
- [x] Company name stored in the watchlist entry; companies of exchanges without a symbol list (EU on the Finnhub free plan) can be added with the name from the search result
- [x] Filter by market (USA / EU / all)
- [x] Filter by sector (sector names as provided by the source)
- [x] In-memory filtering (no database search indexes)

### Search screen
- [x] Search by ticker or first letters of a company name using the default source
- [x] Search starts from the first letter, then on each typed letter, throttled to max 1 request per second; 1- and 2-letter queries match tickers only; at most 25 results
- [x] Results displayed as a company list; each result can be added to the watchlist
- [x] Results show directory data only (no price, indicators or sector); no cache reads
- [x] Exchange name and market from a static exchange table when the source provides no symbol list for the exchange
- [x] Source limit reached or source failure: empty result list with the reason shown under the search box (search status returned by the backend)
- [x] Open detailed screen for a found company

### Company row
- [x] Logo, ticker, company name, flag, stock price
- [x] Short exchange name under the ticker in smaller, weaker font
- [x] Company name limited to half of the card width, truncated with ellipsis
- [x] Market flag (US / EU) as a Unicode character right after the company name
- [x] Market detection (US / EU) by exchange; UK (LSE) and Swiss (SIX) exchanges count as EU; other exchanges have no market (no flag, shown only with the 'all' filter)
- [x] Price color: green if higher than previous cached price, red if lower, default text color (white in dark theme, black in light theme) if equal or no previous data
- [x] Cache indicator under the price: clock symbol + number of days; hidden when data is younger than 1 hour

### Detailed screen
- [x] Tabs: Price and ratio, Balance sheet, Company profile (data from the default source)
- [x] Balance sheet tab: table with one column per year (horizontal scroll, abbreviated large values); SEC EDGAR as an additional source selectable from the dropdown, loaded on demand
- [x] Manual refresh button refreshing all tabs
- [x] UX investigation: side-by-side comparison of several sources (result in `docs/UX-COMPARISON.md` for developer review, not committed; implementation not in scope)

### Sources screen
- [x] List of configured sources with request limits per interval
- [x] Request counters per interval (minute/hour/day)
- [x] Default source marked
- [x] Connection indicator per source for its last call: none on success, response code outside 2XX, 'T' for timeout, 'NPE' for an unexpected application error

### Settings screen
- [x] Settings screen accessible via a settings button
- [x] Switch profile by name (existing profile or a new one)

---

## Beta

### Sources
- [ ] FMP client: search, quote, ratios, dividends, balance sheet, company profile
- [ ] Tiingo client: search, prices, fundamentals available on the free plan, dividends
- [ ] Tiingo monthly limit of unique symbols tracked and respected
- [ ] Default source can be changed on the Sources screen; stored per profile and applied to search and detailed screen
- [ ] Sources screen shows counters for all configured intervals including monthly limits
- [ ] Wider coverage of European exchanges through the new sources, where their plans provide it

### Detailed screen
- [ ] Dividends tab
- [ ] Source dropdown on each tab to switch between sources; one source displayed at a time
- [ ] Data gathered from all sources for comparison (each source respecting its own limits and cache)
- [ ] Company profile tab: add and remove user tags

### Watchlist screen
- [ ] Filter by user tags
- [ ] Optional: map source sector names to a fixed list of predefined sectors

### Indicators
- [ ] Indicator settings stored in Firestore per profile
- [ ] Indicators calculated on the backend and returned with company data
- [ ] Ratio indicators rendered on the company row (ratio names colored by interval); no calculation in the app
- [ ] Intervals per ratio: bad (red), warning (yellow), ok (not displayed), good (green)
- [ ] Indicator settings page: configure intervals per ratio and switch each indicator on/off
- [ ] Default indicator configuration for common ratios (P/E, P/B, ROE, debt/equity, dividend yield, etc.)

### Profiles
- [ ] Per-profile user tags stored in Firestore

---

## RC

### Sources
- [ ] Alpha Vantage client: search, quote, overview (ratios), dividends, balance sheet
- [ ] EODHD client: search, prices, fundamentals, dividends
- [ ] Graceful handling of very low daily limits (Alpha Vantage 25/day, EODHD 20/day): requests only on explicit demand
- [ ] Further European exchange coverage from the added sources (search, prices, ratios, exchange names)

### Settings
- [ ] All app settings consolidated on the settings screen (profile, default source, indicators)
- [ ] Reset indicators to defaults

### Quality and release
- [ ] Cold start time measured and optimised on Cloud Run
- [ ] Logo cache: handling of invalid or broken logo files
- [ ] Empty, loading and error states on all screens
- [ ] Offline behaviour of the app: last loaded data displayed with an error notice
- [ ] Integration tests for each source client against recorded responses
- [ ] End-to-end smoke test of the deployed backend
- [ ] Documentation (READMEs, SETUP.md files, OpenAPI spec) up to date with the implementation

---

## Future improvements
- [ ] EU "source of truth" for balance sheets (expected from the EU in 2027-2029)
- [ ] Side-by-side comparison of several sources on the detailed screen (based on the alpha UX investigation)
- [ ] Company logos from Finnhub: `/stock/profile2` returns a `logo` URL (PNG on `static2.finnhub.io`), usable as a fallback for the current logo source or instead of it (US stocks on the free plan)
