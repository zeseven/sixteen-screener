# Features

Features are grouped by milestone. Each milestone produces a working, deployable solution
(backend + Android app). A feature is checked once it is implemented and covered by tests.

- **Alpha** - end-to-end working app with a minimal set of sources: watchlist, search, detailed screen, profiles.
- **Beta** - source roles (search, US, international) with EODHD for international stocks, manual-only sources, indicators, dividends, tags.
- **RC** - all sources, switching of default sources, source comparison, settings completeness, robustness, polishing and release readiness.

---

## Alpha

### Project and infrastructure
- [x] Repo structure: `backend`, `frontend`, `docs`
- [x] OpenAPI spec `docs/openapi.yaml` (all milestones, marked with `x-milestone`) used by both backend and frontend; alpha endpoints implemented
- [x] Backend: Kotlin + Gradle project with hexagonal architecture (domain, ports, adapters)
- [x] Backend: Ktor application compiled into a GraalVM native image and packaged as a Docker image
- [x] Backend: Cloud Run deployment via gcloud CLI (min instances = 0, startup CPU boost)
- [x] Backend: endpoint access limited to the frontend app's service account and the developer's Google account (Cloud Run IAM)
- [x] Backend: Cloud Run max instances cap
- [x] Backend: Secret Manager integration for source API tokens
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
- [x] Company data cached per ticker and date, shared between users; expires via Firestore TTL after 30 days
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
- [x] Remove by swiping a row to the start past half of its width (a short or fast swipe slides back), with undo in the snackbar that restores the row at its position
- [x] Open detailed screen for a company
- [x] Refresh of stale data when the screen is opened
- [x] Only displayed rows (plus a 5-row buffer) refreshed: on screen open, on scrolling new rows into view, on filter change
- [x] Watchlist stored as entries `{symbol, sector, tags}` and loaded without source requests; sector stored on add and updated by the detailed screen
- [x] Prices and indicators lazy-loaded per displayed row; '-' until loaded; orange after a failed load
- [x] Watchlist stored on the device and shown on start while a fresh one is loaded; adds and removes made during the load are kept
- [x] Company name stored in the watchlist entry; companies of exchanges without a symbol list (EU on the Finnhub free plan) can be added with the name from the search result
- [x] Filter by market (USA / EU / all)
- [x] Filter by sector (sector names as provided by the source)
- [x] In-memory filtering (no database search indexes)

### Search screen
- [x] Search by ticker or first letters of a company name using the default source
- [x] Search starts from the first letter, sent after a 500 ms pause in typing or at once with the keyboard's search action; 1- and 2-letter queries match tickers only; at most 25 results
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
- [x] Name of a security that is not a stock (type other than Common Stock or REIT, e.g. ADR, preference share) in a pale lavender color, on the watchlist, search and detailed screens

### Detailed screen
- [x] Tabs: Price and ratio, Balance sheet, Company profile (data from the default source)
- [x] Balance sheet tab: table with one column per year (horizontal scroll, abbreviated large values); SEC EDGAR as an additional source selectable from the dropdown, loaded on demand
- [x] Manual refresh button refreshing all tabs
- [x] Security type row under the header ('type: ...', red when not a stock, 'unknown' when the source gives none), followed by a checkmark and 'home exchange' when the exchange is the company's home exchange; the company profile is requested as soon as the screen opens
- [x] UX investigation: side-by-side comparison of several sources (result in `docs/UX-COMPARISON.md` for developer review, not committed; implementation not in scope)

### Sources screen
- [x] List of configured sources with request limits per interval
- [x] Request counters per interval (minute/hour/day)
- [x] Default source marked
- [x] Connection indicator per source for its last call: none on success, response code outside 2XX, 'T' for timeout, 'NPE' for an unexpected application error

### Settings screen
- [x] Settings screen accessible via a settings button
- [x] Switch profile by name (existing profile or a new one)
- [x] App version with an automatic build number and commit hash shown in the app section

---

## Beta

### Sources
- [x] Source roles: each source configured with the roles it can serve (`SEARCH`, `US`, `INTERNATIONAL`); the backend picks the source by role (search) or by the company's market (US or international); the app is unaware of the roles
- [x] One default source per role, taken from the configuration for every profile (no switching): Finnhub for search and US, EODHD for international; the global `defaultSource` setting removed
- [x] `automaticRequests` setting per source, on for Finnhub only: a source with it off is called only on a manual refresh from the detailed screen; otherwise stale cached data is returned as is, and without cached data empty data with the `NOT_REQUESTED` status
- [x] A manual refresh calls a source with automatic requests off only when its cached data is older than 1 hour
- [x] Request trigger sent by the app with company data requests (`trigger=MANUAL|AUTO`, replacing `refresh`)
- [x] EODHD client (free plan): delayed quote with previous close, dividends (last year); limits 20 requests per day and per minute; no symbol lists downloaded; `402` (daily limit reached) handled like `429`
- [x] EODHD exchange code per symbol suffix in the static exchange table (`.DE` is `XETRA`), used to build EODHD symbols
- [x] EODHD prices of London and Johannesburg converted from pence and ZA cents into GBP and ZAR
- [x] International (EU and other non-US) prices and dividends through EODHD; ratios, profiles and balance sheets of international stocks `NOT_SUPPORTED` on the free plans
- [x] Sources screen: default search source marked 'default', roles each source is the default for, 'manual only' mark, counters for all configured intervals
- [x] Backup logo source when logoapi.sh has no SVG logo: PNG logo from static2.finnhub.io (URL from the cached Finnhub company profile, otherwise built from the ticker; no Finnhub API calls), stored in the bucket under `finnhub-logos/`; the app renders SVG and PNG logos
- [x] Sources screen: never-resetting total call counter for sources without request limits (Finnhub logos)
- [x] First backup logo source when logoapi.sh has no SVG logo, before Finnhub logos: PNG logo from `https://eodhd.com/img/logos/{EODHD exchange code}/{ticker}.png` (`US/FISI`, `XETRA/SAP`; ticker built like an EODHD symbol; no EODHD API calls and no API token), stored in the bucket under `eodhd-logos/`; separate source without request limits, with a never-resetting total call counter; symbols whose exchange has no EODHD code are skipped

### Detailed screen
- [x] Dividends tab (EODHD for international stocks; no dividends for US stocks until a US dividends source is added)
- [ ] Source dropdown on each tab, limited to the sources configured for the company's market; one source displayed at a time
- [x] Hint to refresh when a source with automatic requests off has no cached data (`NOT_REQUESTED`)

### Watchlist screen
- [ ] Optional: map source sector names to a fixed list of predefined sectors

### Exchanges screen
- [x] Exchanges tab between Search and Sources: a table with one row per symbol suffix (two suffixes of one exchange give two rows) and one row per US exchange (empty suffix); columns: suffix, market flag, short exchange name, full exchange name, MIC, country (with a checkmark for a main venue), currency, time zone
- [x] EODHD exchange code column next to the MIC (empty for exchanges EODHD doesn't cover)
- [x] Table served by the backend from the static exchange table (`GET /exchanges`, full names hardcoded there); no source requests
- [x] The app requests the table once when the screen is first opened and keeps it in memory only (not stored on the device)
- [x] Text filter above the table: case-insensitive match in any field except the flag, done in memory without backend calls

### Indicators
- [x] Curated set of 33 Finnhub ratios (31 usable as indicators, EPS and book value per share on the detailed screen only), TTM or quarterly values with annual fallback
- [x] P/E calculated as price / EPS for indicators when Finnhub returns no P/E and the EPS is negative
- [x] Default indicator settings for all 31 ratios in Firestore (`config/indicatorDefaults`), created from code when missing; only P/E, ROA and ROE switched on
- [x] Indicator settings stored per profile, copied from the defaults on first access; ratios missing in the profile added from the defaults on read
- [x] Settings per ratio: on/off, three thresholds, a level per interval (green, yellow, neutral, red) and a level for values below 0; fixed scale maximum and step per ratio
- [x] Indicators calculated on the backend and returned in display order with watchlist market data and on the price and ratios tab
- [x] Watchlist ratios refreshed when older than 1 day; separate status for indicators (orange only when the ratios failed to load)
- [x] Company row: up to 3 indicators (short names) under each other to the right of the price, `…` after the third when there are more; no calculation in the app
- [x] Detailed screen: ratios colored by their indicator level; `, %` appended to names of percent ratios
- [x] Indicator settings page opened from the settings screen: ratios grouped by category, a checkbox and an interval bar per ratio (draggable thresholds, tap to change an interval color, long press for an exact value)

### User tags
- [x] A user tag is an icon and a text, both required: the icon is chosen from a predefined set of 32 icons, the text is a free text of 1 to 100 characters (trimmed)
- [x] Icon set bundled into the app as vector drawables: Bootstrap Icons, plus chess king, chess queen, poker chip and factory from Tabler Icons (MIT license notices kept with the icons); a tag stores the icon ID, an unknown ID is shown with a fallback icon
- [x] Tags stored per profile in the watchlist entry in Firestore: only watchlist companies have tags, removing a company from the watchlist removes its tags; at most 20 tags per company; two tags with equal icons and texts equal ignoring case are the same tag (no duplicates on one company)
- [x] Company profile tab: tags shown as a list (icon and text) in the first section of the tab, above the profile data; shown and editable also when the profile data is unavailable (source error, reached limit, not supported, not requested); disabled for companies outside the watchlist
- [x] Adding a tag on the company profile tab: an icon picker (grid of the predefined icons) and a text input limited to 100 characters; the tags already used by the profile offered for reuse
- [x] Removing a tag on the company profile tab
- [x] Detailed screen header: icons of the company's tags (without texts) in the row of the security type and home exchange mark, aligned to the right; the row doesn't grow because of them, icons that don't fit are replaced by `…`
- [x] Watchlist screen: no tags on company rows
- [x] Watchlist filter by user tag: each tag used by the profile shown with its icon and text, the text truncated with an ellipsis to fit

---

## RC

### Sources
- [ ] FMP client: search, quote, ratios, dividends, balance sheet, company profile
- [ ] Tiingo client: search, prices, fundamentals available on the free plan, dividends
- [ ] Tiingo monthly limit of unique symbols tracked and respected
- [ ] Alpha Vantage client: search, quote, overview (ratios), dividends, balance sheet
- [ ] Roles and `automaticRequests` of the new sources configured by their limits (Alpha Vantage 25/day: automatic requests off)
- [ ] Default source per role stored per profile and switchable on the Sources screen, limited to the sources configured for the role
- [ ] Detailed screen: data gathered from all sources configured for the company's market for comparison (each source respecting its own limits, cache and automatic requests)
- [ ] Further European exchange coverage from the added sources (search, prices, ratios, exchange names)
- [ ] Static exchange table moved from hardcoded backend code to a file or the database

### Settings
- [ ] All app settings consolidated on the settings screen (profile, default sources, indicators)
- [ ] Reset indicators to defaults

### Quality and release
- [ ] Cold start time measured and optimised on Cloud Run
- [ ] Logo cache: handling of invalid or broken logo files
- [ ] Empty, loading and error states on all screens
- [ ] Offline behaviour of the app: last loaded data displayed with an error notice
- [ ] Integration tests for each source client against recorded responses
- [ ] End-to-end smoke test of the deployed backend
- [ ] Documentation (READMEs, SETUP.md files, OpenAPI spec) up to date with the implementation
- [ ] "New version available" notice: the release step uploads the APK with its build metadata (`output-metadata.json`, holding `versionCode` and `versionName`) to a GCS bucket, the backend reads the latest build from there and returns it to the app, and the app compares it with its own build number

---

## Future improvements
- [ ] EU "source of truth" for balance sheets (expected from the EU in 2027-2029)
- [ ] Side-by-side comparison of several sources on the detailed screen (based on the alpha UX investigation)
- [ ] Detailed screen: show calculated ratio values (e.g. P/E calculated from price and EPS), marked as calculated
- [ ] Search screen turned into a screener: more search fields and functionality to find companies (e.g. by sector, market, exchange, market cap or ratios), based on the screener endpoints of the sources where their plans provide them (FMP, EODHD)
