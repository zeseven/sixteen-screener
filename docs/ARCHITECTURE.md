## Repo structure
|- backend (Kotlin application, deployed to GCP as a Cloud Run service)
|-docs (project documentation, including AI instructions)
|-frontend (Android application)

### Spec
Both frontend and backend use the same file docs/openapi.yaml as a source of truth for endpoints spec.
API specs and references of external sources live separately in docs/api.
Spec is built depending on app features described in REQUIREMENTS.md

## Frontend
Android application used latest UI framework: Jetpack Compose with Material 3.
Minimum Android version: Android 8.0 (API 26). Dynamic colors (Material You) are used on Android 12+ with a static color scheme fallback on older versions.
Theme: follows the system light/dark setting.
Display name: "Screener-16". Launcher icon: adaptive icon built from PNG background, foreground and monochrome (themed icons on Android 13+) layers for every density.
The app only displays indicators returned by the backend; it doesn't calculate them.
Language: Kotlin.
HTTP client: OkHttp with kotlinx.serialization; all calls implemented by hand to match docs/openapi.yaml (no generated client, no Ktor Client). The same OkHttp client is used by Coil for logos.
Dependency injection: Hilt.
Tests: JUnit4.
UI language: English only. All user-visible texts are kept in string resources to allow translations later.
Backend URL: passed to the build only via the `SIXTEEN_BACKEND_URL` environment variable; never saved in a repo file or committed. The build fails with a clear message if it is not set.
Version: `versionName` is a semantic version maintained by hand in `app/build.gradle.kts`. The build number is the number of commits on the first-parent line of the checked-out commit (`git rev-list --count --first-parent HEAD`), calculated by Gradle on every build and never stored in a file; it is the `versionCode` and, with the short commit hash and a flag for uncommitted changes, compiled into `BuildConfig`. The settings screen shows e.g. `0.1.0 (build 156, ff90477)`, with `-dirty` after the hash for a build with uncommitted changes. Release builds are made from `main` (ADR-077).
Watchlist filter selections are kept in memory while the app is running and are not stored on the device.
Data stored on the device (the active profile and the cached watchlist) is excluded from cloud backup and device-to-device transfer.
The watchlist entries of each profile are stored on the device and shown on start while a fresh watchlist is loaded from the backend; prices and indicators are not stored. Adds and removes made while a load is in flight are kept and replayed on the loaded watchlist, which is loaded again afterwards to match the backend (ADR-076).
README contains cli command for building a debug-signed apk file (`assembleDebug`) which should be uploaded via USB, including how to set the backend URL environment variable.

## Backend
Language: Kotlin.
Java version: 21, used everywhere in the backend: Gradle toolchain (local build and tests), Kotlin `jvmTarget` and the GraalVM for JDK 21 native-image build.
Architecture: hexagonal. Each external source has its own configurable Client class. List of sources is configurable via application properties.
Build system: gradle.
Deployed as: Cloud Run service in Docker container.
Expected load: infrequent. Min instances = 0. Expected lots of cold starts. Solution: compile the JVM Kotlin application into a GraalVM native image and enable CR startup CPU boost.
Kotlin/Native is not used, because Google Cloud client libraries (Firestore, Cloud Storage) are JVM-only.
Web framework: Ktor (alternative: Quarkus, see ADR.md).
Dependency injection: none. Dependencies are passed through constructors and wired by hand in the application entry point.
HTTP client for external sources: Ktor Client (CIO engine) with kotlinx.serialization.
Tests: Kotest.
Access to endpoints: limited to the frontend app's service account and the developer's personal google account via Cloud Run IAM (roles/run.invoker). No user sign-in in the app.
The frontend app's service account has no roles other than roles/run.invoker on this Cloud Run service.
Cloud Run max instances is capped to limit cost in case the key leaks.

### Frontend service account key
The service account key is bundled into the APK at build time and is never stored in the git repo.
- The key file lives outside the repo; its location is passed to Gradle via a property in the user-level ~/.gradle/gradle.properties or an environment variable.
- Gradle copies the key into generated build assets (under the build directory), not into the source tree.
- Every build, including unit tests, fails with a clear message if the key location is not configured or the file doesn't exist. Unit tests never use the real key.
- .gitignore contains patterns for key files as an additional safeguard.
- The app uses the key to obtain a Google-signed ID token with the Cloud Run service URL as the audience, and sends it with each request. The app signs the token request (JWT, RS256) itself and exchanges it at the Google token endpoint; no Google auth library is used.
- Key rotation: create a new key, rebuild and reinstall the APK, then delete the old key.
Database: Firestore, in a named database used only by the backend (`sixteen-screener`), not the project's `(default)` database. Cached data is stored with Firestore TTL feature to be automatically removed.
Secret Manager is used for tokens and credentials. Cloud Run exposes them to the container as environment variables (`--set-secrets`); the backend uses no Secret Manager client library.

## Hosting
Backend lives in Google cloud (GCP).
Backend image is built with Cloud Build and pushed to Artifact Registry, then deployed as Cloud Run service; gcloud cli commands are described in backend's README.

## Profiles
Each profile has a UUID assigned by the backend when the profile is created; the profile name is stored as a string field and may contain any characters.
The app gets the profile ID by name from a get-or-create endpoint (on the first start and when switching profiles), stores it locally and uses it in all profile-scoped requests. The profile name is never used in URLs.
Profile names are unique, compared after trimming surrounding whitespace and case-insensitively.

## Caching
Company data is being cahced (saved in Firestore) per company ticker (and date). That cached data is shared between users.
Cached company data expires via Firestore TTL after 14 days; company profiles expire after 90 days. The expiry time is written into each document (`expireAt`), so the TTL period is a code setting.
Quotes are additionally kept as a per-day history: one record per company, source and market day, holding the latest price of that day. Market days are determined in the exchange's time zone. The history follows the same 14-day TTL. The 'previous cached price' is the latest price of the most recent earlier market day.
Company profile data is fetched once and kept until it expires (90 days), then fetched again. It is requested only when a company is added to the watchlist and by the detailed screen; neither the watchlist nor the search requests it.
Same for source-related counters (requests sent to the source this hour/day/month); counters expire after their interval.
Requests are counted before they are sent: a batch of calls (e.g. a watchlist refresh) reserves its budget in one Firestore transaction and makes only the granted number of calls. When a source answers 429, the backend sets its current counters to their limits, so no instance calls the source again until the window resets.
Each backend instance sends at most 10 requests at a time to the same source; further requests wait for a free slot.
The watchlist is stored in the profile as a list of entries `{symbol, name, sector, tags}` in insertion order. The name is stored when the company is added: from the exchange directory when the symbol is listed there, otherwise the name the app sends from the search result, otherwise the ticker. The watchlist endpoint returns these entries with ticker, exchange and market resolved from the exchange directory (or the static exchange table, see Markets); it reads no cached company data and makes no source requests. The sector is taken from the company profile of the profile's default source of the company's role when the company is added (the cached profile if not expired, otherwise requested; when the request fails, the sector of an expired cached profile; `null` only when no profile is known) and updated, together with the name, whenever the detailed screen fetches the company profile from that source and the company is in the requesting profile's watchlist. Watchlists of other profiles are not updated.
Prices and indicators (market data) are loaded with a separate endpoint taking the symbols (at most 50 per request), which refreshes stale quotes within the source limits and returns a status per row. The app calls it for the rows it displays plus a 5-row buffer below them: when the watchlist is shown, when new rows are scrolled into view (each row once per loaded watchlist) and when a filter changes (all displayed rows plus the buffer). The app keeps the market data it has received across watchlist reloads and shows it until newer data arrives.
Search results contain directory data only; the search reads no cached company data.
Watchlist, user tags, indicator settings and (once switching is added) default sources are per-user info, stored with the user profile and used for the user it belongs to. User tags are stored in the watchlist entries, so only watchlist companies have tags.
Watchlist filtering doesn't require any special database feature (like search or index). A watchlist holds at most 250 companies. All the filtering can be done in-memory (either frontend or backend).

## Source calls and failures
Source HTTP clients follow redirects (e.g. Finnhub serves exchange symbol lists through a redirect to a file host); a redirected request counts as one call.
Whether a source is called is decided by the source's `automaticRequests` setting and the request trigger (see Source roles); request limits apply in every case.
If a source call fails (response outside 2XX, timeout, unexpected application error) or the source request limit is reached, the backend returns the latest cached data with a status describing the reason. Without cached data it returns the same status with empty data (null fields, empty lists), which the frontend displays as '-'. Source failures never turn into an error response of the backend.
Search results are not cached in Firestore: when the limit is reached or the source call fails, search returns an empty list with a status (`LIMIT_REACHED`, `SOURCE_ERROR`), and the frontend shows the reason under the search box.
The outcome of the last call to each source is stored in Firestore (shared between instances) and returned by the sources endpoint: success, HTTP status code, timeout (no response received, including connection failures) or unexpected application error. The frontend displays it as a connection indicator: nothing, the code, 'T' or 'NPE'. A request skipped because of a reached limit is not a call and doesn't change the outcome.

## Source roles
Sources differ in request limits and in the data their plans provide, so the backend routes each request by role instead of using one default source. There are three roles:
- `SEARCH` - company search;
- `US` - data of companies listed on US exchanges (symbols without a suffix);
- `INTERNATIONAL` - data of companies listed on any other exchange, EU or not.
The role of a company is taken from its exchange, so it differs from the market (a company of an exchange outside the US and EU markets has no market but has the `INTERNATIONAL` role).

Each source is configured in the application properties with:
- `roles` - the roles it can serve (Finnhub: `SEARCH`, `US`; EODHD: `INTERNATIONAL`). SEC EDGAR and the logo sources serve no role: SEC EDGAR is an additional balance sheet source for US companies, the logo sources are used only for logos.
- `automaticRequests` - whether the source may be called without a manual request of the user (on for Finnhub, off for EODHD).
A `defaultSources` setting names the default source of each role (`search = finnhub`, `us = finnhub`, `international = eodhd`). The configuration is validated on start: each default source must be enabled and configured for its role.
Every profile uses the configured default sources; profiles store none, and switching is not implemented yet. When switching is added, a profile stores its own default source per role, limited to the sources configured for that role, and falls back to the configured default.

The app is unaware of the roles. It asks for the data it needs; the backend takes the profile's default source of the company's role, or the source given in the request. A requested source that isn't configured for the company's role (except SEC EDGAR for the balance sheet of a US company) is answered with `NOT_SUPPORTED` without a request. The watchlist market data endpoint serves each row from the default source of its role, so one request can be served by several sources, each reserving its own budget.

### Request trigger and automatic requests
Every company data request carries a trigger: `MANUAL` for the refresh button of the detailed screen, `AUTO` for anything else (opening the detailed screen, watchlist rows, adding a company to the watchlist).
For a source with automatic requests on:
- `AUTO` from the detailed screen returns cached data as is; when nothing is cached, the source is called;
- `AUTO` from the watchlist market data endpoint returns data younger than 1 hour (ratios: 1 day) and calls the source otherwise;
- `MANUAL` calls the source even if the cache is fresh.
For a source with automatic requests off:
- `AUTO` never calls the source: cached data is returned as is, even when stale (status `CACHED`), and without cached data empty data with the status `NOT_REQUESTED`;
- `MANUAL` calls the source only when the cached data is missing or older than 1 hour; otherwise the cached data is returned.
In both cases a reached request limit, a failure or a timeout returns the cached data with the corresponding status. The app shows `NOT_REQUESTED` data as '-' in the default text color (not orange); the detailed screen adds a hint to refresh.

### EODHD
The free plan provides delayed quotes (with the previous close) and dividends of the last year for global exchanges; ratios, company profiles and balance sheets are not provided (`NOT_SUPPORTED` without a request). Limits: 20 requests per day and 20 per minute; a `402` response (daily limit reached) is handled like a `429`.
An EODHD symbol is built from the ticker and the EODHD exchange code of the symbol suffix in the static exchange table (`SAP.DE` is `SAP.XETRA`). Dots in tickers become dashes (`BRK.B` is `BRK-B`). A symbol whose exchange has no EODHD code is answered with `NOT_SUPPORTED` without a request. No symbol lists are downloaded from EODHD (`directoryExchanges = []`). The API token is sent in the `Authorization: Bearer` header, so it never appears in logged URLs.
The quote price is the `close` field in the currency of the static exchange table; prices of London (`LSE`) and Johannesburg (`JSE`) come in pence and ZA cents and are divided by 100 into GBP and ZAR; the previous close of the response is not used (the previous price comes from the quote history, as for other sources). Dividends of the last year are requested; the dividend yield is the sum of the last 12 months of dividends divided by the latest cached quote of the same source, null without a quote or when the currencies differ.

## Company logos
Being downloaded, logos are stored in a GCS bucket.
Backend looks for a logo in this order, the first hit wins:
1. SVG logo stored in the bucket (`logos/{symbol}.svg`);
2. PNG logo stored in the bucket (`finnhub-logos/{symbol}.png`);
3. SVG logo downloaded from logoapi.sh (curated logos only, ADR-068) and stored;
4. backup: PNG logo downloaded from static2.finnhub.io and stored. The URL is the `logo` field of the cached Finnhub company profile, otherwise built from the ticker (US) or symbol; the Finnhub API is not called. "Finnhub logos" is a separate source without request limits, with a never-resetting total call counter;
5. the default SVG logo.
A source that has no logo records a miss per source and symbol; the source isn't asked again for that symbol for 14 days.
Logos are stored and served in the format provided by the source (SVG or PNG); the backend does no image conversion.
Cache-Control: 30 days for a real logo, 1 day for the default logo when the sources have no logo, 1 hour for the default logo after a source failure, timeout or reached limit.
The frontend renders logos with Coil (coil-svg for SVG) and respects the backend's Cache-Control (coil-network-cache-control). SVG logos are fitted into the logo slot; PNG logos larger than the slot are scaled down, smaller ones are drawn centered at their size in device pixels. If a logo can't be decoded, the frontend shows the default logo.

## Indicators
Indicators are calculated on the backend and returned together with company data; the frontend only renders them.

### Ratios
The backend maps a curated set of Finnhub `/stock/metric` keys to shared ratio codes. Each ratio has a code, a full name, a short name (at most 6 characters, used on the company row), a unit (`PERCENT`, `RATIO`, `CURRENCY`) and a category (valuation, profitability, margins, growth, liquidity, leverage, efficiency, dividends, risk, per share). Values keep Finnhub's units: percent ratios are percents (`15` is 15%). A ratio takes the first value present among its keys (TTM or quarterly first, then annual). The app appends `, %` to the full names of percent ratios.
Ratios of the per share category (EPS, book value per share) are amounts in currency: they are shown on the detailed screen only and can't be indicators. When the symbol of a Finnhub response differs from the requested one (`BRK.B` is answered with `BRK.A` data), per share values are dropped.
When Finnhub returns no P/E and the EPS is negative, the P/E used for indicators is calculated as price / EPS from the quote of the same source; the detailed screen still shows the value returned by the source.

### Indicator settings
A setting of one ratio has an on/off switch, three thresholds `t1 <= t2 <= t3` and a level for each of the four intervals `[0, t1)`, `[t1, t2)`, `[t2, t3)`, `[t3, +inf)`, plus a level for values below 0. A level is `GOOD`, `WARNING`, `NEUTRAL` or `BAD`; a `NEUTRAL` value, a missing value and a switched off ratio make no indicator. Equal thresholds make an empty interval.
Each ratio also has a fixed scale maximum and step, used by the settings page to draw and move the thresholds; values above the scale maximum belong to the last interval like any value above `t3`.

Default settings are kept in Firestore in the `config/indicatorDefaults` document. The backend creates it from the values in code when it is missing and never overwrites it, so the defaults can be tuned in the database. The scale maximum and step of each ratio come from this document only.
Profile settings are stored in the profile document. When the settings of a profile are read (by the settings endpoint or to calculate indicators) and the profile has none, the defaults are copied into the profile in a transaction; ratios present in the defaults but missing in the profile are added the same way. After the copy, the profile's settings are used and later changes of the defaults don't affect them.

### Display order
The backend returns the indicators of a company in display order: one of each level in the order `GOOD`, `WARNING`, `BAD` (the first in settings order within a level), the free places of the first three filled from the biggest level group (on a tie, from the group whose next indicator comes first in settings order), the three sorted by level in the same order, and then the remaining indicators by level and settings order. The company row shows the first three under each other to the right of the price and `…` after the third when there are more. The detailed screen colors each ratio with the level of its indicator.

### Freshness
The watchlist market data endpoint reads cached ratios together with quotes and refreshes ratios older than 1 day (quotes keep the 1 hour freshness). Quote and ratio requests share the per-source budget reservation; quotes are served first. The status of the ratios is returned separately from the quote status, and the app draws the indicators in orange only when the ratios failed to load.

## Markets
Market is detected by exchange. US exchanges map to the USA market.
EU exchanges, together with UK (LSE) and Swiss (SIX) exchanges, map to the EU market (EU flag and filter).
Exchanges outside both markets have no market: such companies get no flag and are shown only with the 'all' market filter.
Exchange data (symbol lists per exchange with exchange code, name and currency) is downloaded from the default search source when first needed, only for the exchanges listed in the source's `directoryExchanges` setting (`[US]` for Finnhub, `[]` for EODHD; without the setting, for any exchange); no list is requested for any other exchange. A downloaded list is kept for 7 days, saved as a file in the GCS bucket and kept in memory. A symbol missing from a list older than 1 day triggers one new download of that list (at most once per day per exchange). It is used to resolve the exchange name and market of search results and watchlist rows without extra source requests, and to validate symbols. The exchange of a symbol is taken from its suffix (`SAP.DE`, `SHEL.L`; no suffix means US).
A static exchange table maps each known exchange code to its exchange short name, market, time zone and currency (`AS` is AMS, EU, Europe/Amsterdam, EUR). The short name is shown under the ticker on the company row, so it is at most 6 characters: the city name when it fits, otherwise the usual abbreviation of the exchange (`XFRA` is FSE, `XBUD` is BET). The table is the fallback when the source provides no symbol list for an exchange: the Finnhub free plan provides the US list only. Symbols of such exchanges are accepted without validation when added to the watchlist, with the name from the search result. Their data comes from the international source (see Source roles).

The static exchange table also holds the full exchange name and the EODHD exchange code (`XETRA` for `DE`, `LSE` for `L`; none for exchanges EODHD doesn't cover), and is served as is by `GET /exchanges` for the Exchanges screen: one row per known symbol suffix and one row per US exchange (empty suffix). The app requests it once and keeps it in memory for the lifetime of the process; filtering is done by the app.

## Source data
Each source's data is displayed as provided by that source, without cross-source normalisation.
For Finnhub (free plan) the balance sheet comes from reported financials, i.e. raw data as filed with SEC EDGAR.
SEC EDGAR is kept as a separate source to double-check balance sheets from other sources.
