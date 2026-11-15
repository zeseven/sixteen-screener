# Architecture Decision Records

Decisions are listed in historical order.

## ADR-001: Milestones and source distribution

**Date:** 2026-11-13
**Status:** Accepted

**Context:** The app integrates many external sources. Delivering all of them at once delays a working solution.

**Decision:** Features are split into three milestones (alpha, beta, RC), each producing a working backend + Android app.
External sources are distributed as follows:
- Alpha: logoapi.sh (logos), Finnhub (prices, ratios, search, profile, balance sheet), SEC EDGAR (US balance sheet source of truth)
- Beta: Financial Modeling Prep, Tiingo
- RC: Alpha Vantage, EODHD

**Consequences:** The feature list is maintained in `docs/FEATURES.md`.

## ADR-002: Finnhub is the default source in alpha

**Date:** 2026-11-13
**Status:** Accepted

**Context:** Alpha has only one price/ratio source, and it has the most generous limits (60 calls/minute).

**Decision:** Finnhub is the default source in alpha. Default source switching on the Sources screen is delivered in beta, when more than one price/ratio source exists.

## ADR-003: Feature distribution across milestones

**Date:** 2026-11-13
**Status:** Accepted

**Decision:**
- Alpha: infrastructure, profiles, watchlist (add/remove, market and sector filters), search, company row with price coloring and cache indicator, detailed screen (price and ratio, balance sheet with SEC EDGAR, company profile), read-only Sources screen, settings screen with profile switching.
- Beta: dividends tab, per-tab source dropdown and cross-source comparison, default source switching, user tags and tag filter, indicators and indicator settings.
- RC: low-limit sources, further European exchange coverage, settings consolidation, quality and release hardening.

**Rationale:** Dividends and comparison only make sense with multiple sources (Finnhub's dividend endpoints are not part of its free plan). Tags and indicators are not required for a working end-to-end solution.

## ADR-004: Jetpack Compose for the Android UI

**Date:** 2026-11-13
**Status:** Accepted

**Context:** ARCHITECTURE.md requires the latest Android UI framework.

**Decision:** The Android app uses Jetpack Compose with Material 3.

## ADR-005: GraalVM native image with Ktor for the backend

**Date:** 2026-11-13
**Status:** Accepted

**Context:** The backend runs on Cloud Run with min instances = 0 and frequent cold starts. Kotlin/Native was proposed for fast startup, but the Google Cloud client libraries (Firestore, Cloud Storage, Secret Manager) are JVM-only, so Kotlin/Native would require hand-written REST clients for every GCP service.

**Decision:** The backend is a JVM Kotlin application built with Gradle and compiled into a GraalVM native image. Web framework: Ktor. Cloud Run startup CPU boost is enabled.

**Alternatives:** Quarkus (Kotlin support, first-class GraalVM native build tooling, CDI). Considered a fallback if Ktor native-image configuration (reflection, serialization, GCP libraries) turns out to be too costly to maintain.

## ADR-006: Market marker and filter in alpha

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** Market detection (US / EU), the market flag and the market filter are delivered in alpha. Companies of every exchange a source provides are available from the start. Sources added in beta and RC widen the coverage of European exchanges.

## ADR-007: Detailed screen shows one source at a time

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** Each tab of the detailed screen displays data from one source, selected via a dropdown (default source preselected). Side-by-side comparison is a future improvement; its UX is investigated during alpha.

## ADR-008: Indicators are calculated on the backend

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** Indicator settings are stored in Firestore. The backend calculates indicators and returns them together with company data. The app only renders the returned indicators.

**Consequences:** Indicator logic and settings live in one place; the app does not need ratio interval logic.

## ADR-009: Location of API specs

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** The OpenAPI spec of this app lives in `docs/openapi.yaml`, next to REQUIREMENTS.md. Specs and references of external sources live in `docs/api/`.

## ADR-010: Sector names

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** In alpha, sector names are taken as-is from the source data. Mapping source sectors to a fixed list of predefined sectors is an optional beta feature.

## ADR-011: Minimum Android version

**Date:** 2026-11-13
**Status:** Accepted

**Context:** The app should use modern Android UI and still install on older phones. Jetpack Compose and Material 3 work on all versions supported by current AndroidX (API 23+). The only notable Material 3 feature limited to Android 12+ (API 31) is dynamic color (Material You).

**Decision:** minSdk = 26 (Android 8.0). Dynamic colors are used on Android 12+, with a static color scheme fallback on older versions.

**Rationale:** API 26 keeps compatibility with older phones while providing native `java.time` and adaptive icons, avoiding desugaring and legacy workarounds. Raising minSdk to 31 would only remove the dynamic color fallback.

## ADR-012: Per-profile default source and indicator settings

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** The default source and indicator settings are stored per profile, like the watchlist and user tags. Indicators are calculated on the backend using the requesting profile's settings.

## ADR-013: Cached company data TTL

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** Cached company data expires via Firestore TTL after 14 days. Request counters expire after their own interval (minute, hour, day, month).

## ADR-014: UK and Swiss exchanges belong to the EU market

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** For market detection, the EU flag and the market filter, UK (LSE) and Swiss (SIX) exchanges count as EU.

## ADR-015: Source data is shown as provided

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** Each source's data is displayed as returned by that source, without normalisation or merging. Finnhub is used on the free plan; its balance sheet comes from reported financials (raw data as filed with SEC EDGAR). SEC EDGAR remains a separate source to double-check balance sheets from other sources.

## ADR-016: SVG logos rendered by the app

**Date:** 2026-11-13
**Status:** Accepted

**Context:** Logos come from logoapi.sh in SVG format. Android can't render SVG without an additional library.

**Decision:** The backend stores and serves logos as SVG without conversion. The app renders them with Coil and coil-svg (AndroidSVG). If a logo can't be decoded, the app shows the default logo.

**Alternatives:** Converting SVG to PNG on the backend (Apache Batik, JSVG or resvg). Rejected: Batik and JSVG depend on AWT, which has limited GraalVM native-image support; resvg adds a binary and subprocess calls. resvg remains an option for a backend-side fallback if some logos render poorly.

## ADR-017: Service account key bundled into the APK

**Date:** 2026-11-13
**Status:** Accepted

**Context:** Cloud Run endpoints are restricted by IAM. The app needs a way to authenticate without adding steps for end users.

**Decision:** The app authenticates with a dedicated service account whose key is bundled into the APK at build time. The key file lives outside the repo and is injected by Gradle into generated build assets; it is never committed. The app mints ID tokens from the key with the Cloud Run URL as the audience.

**Mitigations:** The service account only has roles/run.invoker on this service; Cloud Run max instances is capped; the key is rotated by issuing a new key and rebuilding the APK.

**Alternatives:** Google sign-in with Cloud Run custom audiences and a Google Group granted run.invoker. Rejected: adds a login step for users and group management for the developer. Shared API key with a public Cloud Run service. Rejected: weaker than IAM with no usability gain.

**Consequences:** Anyone who obtains the APK can extract the key and call the backend (consume source request limits, read and change watchlists). They get no direct access to Firestore, GCS or Secret Manager. Requires the GCP organization policy to allow service account key creation.

## ADR-018: API design conventions

**Date:** 2026-11-13
**Status:** Accepted

**Decision:**
- The spec uses OpenAPI 3.0.3 for the widest code generator support (Kotlin server and client).
- One spec covers all milestones; operations and properties are marked with `x-milestone` (alpha, beta, rc).
- Endpoints depending on per-user data are scoped by profile: `/profiles/{profileName}/...`. Profiles are created implicitly on the first write. (Superseded by ADR-025.)
- Companies are identified by a canonical `symbol` assigned by the backend (ticker for US, ticker with exchange suffix otherwise); the backend maps it to each source's own format.
- Each detailed screen tab has its own endpoint with optional `source` (defaults to the profile's default source) and `refresh` parameters. Responses carry the source, fetch time and a data status (fresh, cached, limit reached, source error, not supported, no data).
- The backend returns the price trend against the previous cached price; the app derives the cache indicator from `fetchedAt` using the device's local date.
- Watchlist filtering is done in the app; the watchlist endpoint returns market, sector and tags for each company.
- Search returns cached quotes only, to avoid spending source requests on every search result.
- Errors use RFC 9457 problem details.

## ADR-019: Test frameworks

**Date:** 2026-11-13
**Status:** Accepted

**Decision:** Android tests use JUnit4 (the default for AndroidX test, Compose UI tests and Robolectric). Backend tests use Kotest.

**Consequences:** Frontend and backend have different test styles; each follows its platform's standard tooling.

## ADR-020: Hand-written Android HTTP client

**Date:** 2026-11-13
**Status:** Accepted

**Context:** `docs/openapi.yaml` is the source of truth for endpoints. A client could be generated from it, or built on Ktor Client to match the backend.

**Decision:** The Android app uses standard Android HTTP libraries. All calls are implemented by hand, matching `docs/openapi.yaml`. No generated client and no Ktor Client.

**Consequences:** Any change to `docs/openapi.yaml` requires a matching manual change in the Android client.

## ADR-021: Dependency injection

**Date:** 2026-11-13
**Status:** Accepted

**Context:** The Android app and the backend have different needs. Hilt is the Android standard and checks wiring at compile time. The backend has a small object graph and is compiled into a GraalVM native image.

**Decision:** The Android app uses Hilt. The backend uses no DI library: dependencies are passed through constructors and wired by hand in the application entry point.

**Consequences:** Backend wiring is plain Kotlin code with no reflection or code generation, which suits the native image. Koin examples in backend skills are not followed.

## ADR-022: Secrets delivered as environment variables by Cloud Run

**Date:** 2026-11-14
**Status:** Accepted

**Context:** Source API tokens and credentials are kept in Secret Manager. Reading them with the Secret Manager client library adds a dependency that needs native-image configuration and an extra call on every cold start.

**Decision:** Secrets stay in Secret Manager and are exposed to the container as environment variables with `gcloud run deploy --set-secrets`. The backend reads them from the environment and uses no Secret Manager client library.

**Consequences:** The runtime service account needs `roles/secretmanager.secretAccessor` on each secret. Secret values are resolved when an instance starts; a new secret version reaches the service with the next revision or instance start. Locally the same variables are set by hand.

## ADR-023: Backend image built with Cloud Build

**Date:** 2026-11-14
**Status:** Accepted

**Context:** Cloud Run runs `linux/amd64` images. Building a GraalVM native image for `linux/amd64` under emulation on an Apple Silicon Mac is very slow and memory-hungry.

**Decision:** The backend image is built with Cloud Build (`gcloud builds submit` with `cloudbuild.yaml`) on an `E2_HIGHCPU_8` machine, using a dedicated build service account, and pushed to Artifact Registry. Local `docker build` remains possible on amd64 hosts.

**Consequences:** One-time setup includes the Cloud Build API and a build service account with push access to the Artifact Registry repository. Builds need network access to GCP; Cloud Build usage beyond the free tier is billed.

## ADR-024: Ktor Client for outgoing calls from the backend

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The backend calls external sources (Finnhub, SEC EDGAR, logoapi.sh) over HTTP. ADR-020 rules out Ktor Client for the Android app only.

**Decision:** Backend source clients use Ktor Client with the CIO engine and kotlinx.serialization, the same stack as the Ktor server.

**Consequences:** One HTTP and serialization stack on the backend, with no reflection-based libraries to configure for the native image. Source clients are tested with Ktor `MockEngine` and recorded responses.

## ADR-025: Profiles identified by a backend-generated ID

**Date:** 2026-11-14
**Status:** Accepted

**Context:** A profile name can be any string. Using it in URL paths and as a Firestore document ID requires encoding and breaks on characters such as `/`.

**Decision:**
- Each profile has a UUID assigned by the backend when the profile is created. The name is stored as a plain string field.
- `POST /profiles` with a name returns the existing profile with that name or creates a new one (get-or-create). The app calls it on the first start and when switching profiles, stores the returned ID and uses it in all profile-scoped requests (`/profiles/{profileId}/...`).
- Profile names are unique. They are compared after trimming surrounding whitespace, case-insensitively; the name is stored as first entered (trimmed).
- Profile-scoped endpoints return `404` for an unknown profile ID; profiles are no longer created implicitly.

**Consequences:** Supersedes the profile scoping bullet of ADR-018. Name uniqueness is enforced by the backend in a Firestore transaction.

## ADR-026: Detailed screen data fetched when nothing is cached

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The detailed screen shows cached data without requesting the source on open. A company opened from search may have no cached data at all.

**Decision:** Detailed screen endpoints return cached data as is. When nothing is cached for the requested source and data type, the backend requests the source automatically, within its request limits. A manual refresh (`refresh=true`) requests the source even if the cache is fresh.

## ADR-027: Source failures return cached or empty data

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** When a source returns a response outside 2XX, times out, fails with an unexpected application error or its request limit is reached, the backend returns the latest cached data with the corresponding status (`SOURCE_ERROR` or `LIMIT_REACHED`). Without cached data it returns the same status with empty data: nullable fields are null and lists are empty. The app displays missing values as "-". Source failures don't produce error responses of the backend. Delivered in alpha.

**Consequences:** The app always gets a renderable response for company data. Source failure handling is no longer a separate RC feature; every source client added later follows the same rule.

## ADR-028: Source connection indicator

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** The backend stores the outcome of the last call to each source in Firestore and returns it with the sources list. Outcomes: success, HTTP status outside 2XX (with the code), timeout (no response received, including connection failures), unexpected application error (e.g. a response that can't be parsed or mapped). The Sources screen shows nothing for success, the response code, "T" for timeout and "NPE" for an unexpected application error. Requests skipped because a limit is reached are not calls and don't change the outcome. Delivered in alpha, together with the read-only Sources screen.

## ADR-029: Search returns a status instead of an error

**Date:** 2026-11-14
**Status:** Accepted

**Context:** ADR-027 returns cached or empty data with a status when a source fails. Search results are not cached in Firestore, so a failed search can only return an empty list.

**Decision:** The search endpoint always answers `200`. `SearchResults` carries a `status`: `OK` (the source was searched or the same query was answered recently; items may be empty), `LIMIT_REACHED` or `SOURCE_ERROR` (items are empty). The `429` response is removed. The app shows the reason under the search box.

## ADR-030: Java 21 for the backend

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The backend runs as a GraalVM native image, which carries the class library of the JDK it was built with. Different Java versions for compilation, tests and the native build gain nothing and let tests run on a different class library than production.

**Decision:** The backend uses Java 21 everywhere: Gradle Java toolchain (local build and tests), Kotlin `jvmTarget` and the native-image build stage (GraalVM for JDK 21).

**Alternatives:** Java 25 (newer LTS). Postponed: Java 21 is the established choice for Kotlin, Ktor, the Google Cloud libraries and GraalVM native image.

**Consequences:** A JDK 21 is required on the developer machine for local builds and tests.

## ADR-031: Bulk request reservation and saturation on 429

**Date:** 2026-11-14
**Status:** Accepted

**Context:** Request counters are shared between instances in Firestore. A watchlist refresh may need dozens of calls to one source at once; one transaction per call would contend on the same counter document. Our fixed counter windows can also disagree with the provider's own accounting (bursts across a window boundary, other users of the same API key).

**Decision:**
- Calls are reserved before they are sent. A batch reserves its budget in one Firestore transaction: it reads the current window of every configured interval, grants `min(requested, remaining)` calls, adds the granted amount to every window and then makes exactly that many calls.
- When a source answers `429`, the backend sets the count of every current window of that source to its limit. All instances stop calling the source until a window resets; the remaining calls of the batch are skipped and return cached data with `LIMIT_REACHED`.

**Consequences:** Counters can only overcount (e.g. calls reserved but not made), which at worst returns cached data slightly earlier than necessary. The provider's `429` corrects undercounting.

## ADR-032: Company profile fetched once per TTL period

**Date:** 2026-11-14
**Status:** Accepted

**Context:** Company profile data (name, sector, industry, exchange) rarely changes, while source request limits are tight.

**Decision:** Company profile data is fetched from the source when absent and kept until it expires (14 days, ADR-013); then it is fetched again. Expiry is checked from the fetch time, not from Firestore TTL deletion. The hourly watchlist refresh requests only quotes. A manual refresh on the detailed screen still requests the profile.

## ADR-033: Per-day quote history

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The price color needs the previous cached price. Future features may use more price history.

**Decision:**
- Each quote write also stores the price in a per-day history: one record per source, company and market day, holding the latest price of that day.
- Market days are determined in the exchange's time zone.
- The history follows the 14-day TTL of cached company data.
- For now only the latest quote and the previous market day's price (for the price color) are used. The latest quote record carries the previous day's price, so reading it needs no history query.

## ADR-034: Exchange directory in the GCS bucket

**Date:** 2026-11-14
**Status:** Accepted

**Context:** Search results of the source (Finnhub `/search`) contain no exchange, while the company row shows the short exchange name and the market flag. Requesting a company profile per search result would spend source requests on every keystroke.

**Decision:**
- The backend keeps a symbol list per exchange from the source (Finnhub `/stock/symbol?exchange={code}`, includes the exchange MIC and currency).
- The exchange code is taken from the symbol suffix (`SAP.DE` is `DE`, `SHEL.L` is `L`; no suffix is `US`).
- A list is downloaded the first time a symbol of that exchange is seen, then at most once per day (one request per exchange), saved as a JSON file in the GCS bucket and held in memory.
- It resolves exchange, market, currency and name of search results and watchlist rows, and validates symbols.

## ADR-035: Exchanges outside the US and EU markets

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** Search returns every company the source finds. Companies listed on exchanges that belong to neither the US nor the EU market (e.g. Tokyo, Toronto) have `market = null`: no flag, shown only with the "all" market filter.

## ADR-036: SEC ticker table download counted against the SEC EDGAR limit

**Date:** 2026-11-14
**Status:** Accepted

**Context:** Resolving a ticker to a CIK may require downloading `company_tickers.json` from the SEC in addition to the company facts request. The caller reserves one call per balance sheet request.

**Decision:** The ticker table download reserves its own extra call against the `sec-edgar` limit. If that call isn't granted, the balance sheet request returns `LIMIT_REACHED` without calling the SEC. The outcome of a failed or `429` download is recorded as the call outcome.

**Consequences:** The download happens rarely (the table is kept in memory and resolved CIKs are cached in Firestore for 14 days), so the extra reservation has no practical effect on the limit.

## ADR-037: Resolution of duplicated and amended SEC filings

**Date:** 2026-11-14
**Status:** Accepted

**Context:** SEC company facts repeat balance sheet values: each 10-K also reports the prior year, and 10-K/A amendments may restate part of a filing.

**Decision:** Only instant `us-gaap` facts from `10-K` / `10-K/A` with `fp = FY` are used, one period per period end, newest first. For each period, filings are ranked: the filing whose own balance sheet date equals the period end first, then the latest filing date, then amendments. Each line item is taken from the best-ranked filing that reports it, so a partial amendment replaces only the values it reports and prior-year values only fill gaps.

## ADR-038: Ticker form for logoapi.sh

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** US symbols are requested by their plain ticker (`AAPL`, `BRK.B`); other symbols by the full symbol with suffix (`SAP.DE`), because a bare non-US ticker may match the logo of an unrelated US company. Requests use `fallback=404`, and a response with the `X-Logo-Fallback` header is also treated as a miss.

**Consequences:** logoapi.sh doesn't document non-US tickers; a miss is recorded and the default logo is returned.

## ADR-039: Combined status of the price and ratios tab

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The price and ratios endpoint combines two cached data types (quote and ratios) under one `SourceDataMeta`.

**Decision:** Parts with `NOT_SUPPORTED` or `NO_DATA` are ignored. If nothing is left, the status is `NO_DATA` when any part had no data, otherwise `NOT_SUPPORTED`. Otherwise the worst remaining status wins: `SOURCE_ERROR` > `LIMIT_REACHED` > `CACHED` > `FRESH`. `fetchedAt` is the oldest `fetchedAt` of the returned data, or null when nothing is returned.

## ADR-040: Symbol suffixes that are not exchange codes

**Date:** 2026-11-14
**Status:** Accepted

**Context:** US share classes use a dot in the ticker (`BRK.B`), which looks like an exchange suffix (ADR-034).

**Decision:** A suffix is treated as an exchange code only if it is a known Finnhub exchange code. Otherwise the whole symbol is a US ticker.

## ADR-041: Companies missing from the exchange directory

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The exchange directory may not contain a symbol (stale list, exchange not available on the source plan) or may be temporarily unavailable.

**Decision:**
- Search hits not found in the directory are kept, with the exchange code as the exchange name, the name from the source and `market = null`.
- Watchlist rows whose symbol is not found are kept with the same best-effort identity (name from the cached company profile when present) and the cached quote, which is not refreshed because the currency is unknown.
- Adding a symbol that is already in the watchlist skips the directory check, so the operation stays idempotent while the directory is unavailable. Adding a new symbol that is not in the directory returns `404`.

## ADR-042: Missing service account key fails every build

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The service account key is injected into the APK at build time from a location outside the repo (ADR-017). The build could fail either for every task or only for APK assembly.

**Decision:** Every build of the Android app fails with a clear message when the key location is not configured or the file doesn't exist, including `./gradlew test`.

**Consequences:** The key must be configured on every machine that builds or tests the app. Unit tests never use the real key; they generate a fake key in the test code.

## ADR-043: English-only app with string resources

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** The Android app is in English only. All user-visible texts are kept in string resources, so translations can be added later without code changes.

## ADR-044: Backend URL passed as an environment variable

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The Android app needs the Cloud Run service URL as the base URL of all calls and as the ID token audience.

**Decision:** The backend URL is passed to the Android build only through the `SIXTEEN_BACKEND_URL` environment variable. It is never saved in a file of the repo, in `gradle.properties` or committed. The build fails with a clear message when the variable is not set or is not an absolute `https` URL. The build instructions in `frontend/README.md` explain how to set it.

**Consequences:** The URL is set in the shell before every build (e.g. from `gcloud run services describe`).

## ADR-045: Watchlist filters are not persisted

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** Market and sector filter selections on the Watchlist screen are kept in memory only while the app is running. They are not stored on the device; every app start shows the unfiltered watchlist.

## ADR-046: Side-by-side comparison UX study kept outside the repo history

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** The result of the alpha UX investigation of side-by-side source comparison (ADR-007) is written into a separate Markdown document, `docs/UX-COMPARISON.md`, for the developer's manual review. The document is not committed; the developer decides on its further use.

## ADR-047: SEC EDGAR always selectable on the balance sheet tab

**Date:** 2026-11-14
**Status:** Accepted

**Context:** SEC EDGAR provides balance sheets for US companies only.

**Decision:** The balance sheet source dropdown always offers SEC EDGAR, regardless of the company's market. For companies SEC EDGAR doesn't cover, the app renders the `NOT_SUPPORTED` status returned by the backend.

**Consequences:** No market-specific logic in the app. When an EU source of truth appears (future improvement), the same dropdown and status handling apply.

## ADR-048: OkHttp as the Android HTTP library

**Date:** 2026-11-14
**Status:** Accepted

**Context:** ADR-020 requires a hand-written client on standard Android HTTP libraries. Logos are served by the same IAM-protected backend and are loaded by Coil (ADR-016), which needs an authenticated HTTP client too.

**Decision:** The Android app uses OkHttp with kotlinx.serialization. All calls are still written by hand following `docs/openapi.yaml` (ADR-020). One OkHttp client with an authentication interceptor is shared by the API client and Coil (`coil-network-okhttp`).

**Alternatives:** `HttpURLConnection` only. Rejected: logos would need a custom Coil fetcher, and tests would lack OkHttp's MockWebServer.

**Consequences:** API client tests use MockWebServer.

## ADR-049: ID token minted by the app without Google auth libraries

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The app needs a Google-signed ID token with the Cloud Run URL as the audience, minted from the bundled service account key (ADR-017).

**Decision:** The app mints the token itself: it builds a JWT (RS256, signed with `java.security` using the key's private key) with `target_audience` set to the backend URL, exchanges it at the key's `token_uri` (`https://oauth2.googleapis.com/token`) and caches the returned ID token in memory until 5 minutes before it expires. No `google-auth-library` or other Google auth dependency is used.

**Alternatives:** `com.google.auth:google-auth-library-oauth2-http` (`ServiceAccountCredentials` + `IdTokenCredentials`). Rejected: large transitive dependencies (Guava, google-http-client) for one small flow.

**Consequences:** Key parsing, JWT signing, token caching and refresh are covered by the app's own unit tests.

## ADR-050: Light and dark theme with dynamic colors

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** The app follows the system light/dark setting. It uses the dynamic color scheme on Android 12+ and a static light/dark scheme on older versions (ADR-011). An unchanged price (equal to the previous cached price, or no previous price) uses the default text color: white in the dark theme, black in the light theme. Higher and lower prices are shown in green and red, with light and dark variants chosen for readability on the respective background.

**Consequences:** "White" for an unchanged price in REQUIREMENTS.md means the default text color of the current theme.

## ADR-051: Debug-signed APK

**Date:** 2026-11-14
**Status:** Accepted

**Context:** The APK is installed over USB on a few devices; it is not published to a store.

**Decision:** The app is built and installed as a debug-signed APK (`./gradlew assembleDebug`). No release keystore and no R8 configuration are maintained.

**Consequences:** The debug keystore is per machine (`~/.android/debug.keystore`). An APK built on another machine can't update an installed one; the app has to be uninstalled first, which deletes the locally stored profile (it is restored by entering the same profile name).

## ADR-052: Detailed screen refresh button refreshes all tabs

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** The refresh button of the detailed screen reloads all tabs with `refresh=true`: price and ratio, balance sheet and company profile. The balance sheet tab refreshes the currently selected source; results of other sources kept on the screen are discarded and loaded again when selected.

**Consequences:** One refresh spends up to one source request per tab.

## ADR-053: Balance sheet shown as a table with one column per year

**Date:** 2026-11-14
**Status:** Accepted

**Decision:** The balance sheet tab shows a table: one row per line item (label as provided by the source), one column per fiscal year, newest year first. The table scrolls horizontally when the years don't fit the screen width; the label column stays visible. Large values are abbreviated (thousands `K`, millions `M`, billions `B`, trillions `T`, e.g. `352.6 B`); missing values are shown as `-`.

## ADR-054: Search from the first character

**Date:** 2026-11-15
**Status:** Accepted

**Context:** Some tickers have one or two letters (e.g. `F`, `GM`). With a 3-character minimum (ADR-029, REQUIREMENTS) they can't be found.

**Decision:** The app sends a search request from the first typed character, at most one request per second: the first request goes out immediately, then the latest query once the second has passed, and a newer query cancels the request in flight. An empty input (after trimming) clears the results without a request. The backend accepts queries of 1 to 50 characters after trimming. Queries of 1 or 2 characters match tickers only: the backend keeps the source's hits whose ticker starts with the query, exact ticker matches first; longer queries match tickers and company names as before. Every search response is limited to 25 results.

**Consequences:** Typing spends up to one source request per second from the first letter. The 5-minute in-memory search cache and the 25-result limit keep responses small for short queries.
