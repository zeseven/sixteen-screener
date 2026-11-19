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
**Status:** Accepted; the `X-Logo-Fallback` rule is replaced by ADR-068

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

**Decision:** (Request timing superseded by ADR-082.) The app sends a search request from the first typed character, at most one request per second: the first request goes out immediately, then the latest query once the second has passed, and a newer query cancels the request in flight. An empty input (after trimming) clears the results without a request. The backend accepts queries of 1 to 50 characters after trimming. Queries of 1 or 2 characters match tickers only: the backend keeps the source's hits whose ticker starts with the query, exact ticker matches first; longer queries match tickers and company names as before. Every search response is limited to 25 results.

**Consequences:** Typing spends up to one source request per second from the first letter. The 5-minute in-memory search cache and the 25-result limit keep responses small for short queries.

## ADR-055: Watchlist quotes refreshed only for displayed rows

**Date:** 2026-11-15
**Status:** Accepted

**Context:** `GET /watchlist` refreshed every stale quote of the watchlist at once. Most of those rows are never looked at, while each refresh spends source requests (Finnhub free plan: 60 per minute); a larger watchlist makes it worse.

**Decision:** (Endpoint and payload superseded by ADR-057 and ADR-058.) `GET /watchlist` returns cached quotes as is and makes no quote requests. A new endpoint `GET /watchlist/refresh?symbols=...` (1 to 50 symbols, only those in the watchlist) refreshes stale quotes of the given rows within the source limits and returns them. The app calls it for the displayed rows plus 5 rows below them: when the watchlist is shown, when rows that have not been refreshed since the watchlist was loaded scroll into view, and when a filter changes (all displayed rows plus the buffer). Absent or expired company profiles are still requested by `GET /watchlist`, because the sector filter needs the sector of every row; they are fetched once per 14 days per company (ADR-032).

**Consequences:** Opening the watchlist spends quote requests for about one screen of rows instead of the whole list. Rows scrolled into view show cached data until their refresh arrives. Scrolling through the whole list still spends one request per stale row.

## ADR-056: Watchlist holds up to 250 companies

**Date:** 2026-11-15
**Status:** Accepted

**Context:** The watchlist was limited to 100 companies. Since ADR-055, `GET /watchlist` makes no quote requests, so the size of the watchlist no longer drives source requests.

**Decision:** A watchlist holds at most 250 companies. Adding a company that is not in the watchlist yet to a full watchlist returns `400`.

**Consequences:** `GET /watchlist` returns up to 250 rows; filtering stays in memory in the app. Absent or expired company profiles are still requested by `GET /watchlist`, so filling a watchlist quickly spends up to one profile request per new company.

## ADR-057: Watchlist entries store symbol, sector and tags

**Date:** 2026-11-15
**Status:** Accepted

**Context:** The watchlist screen needs the name, exchange and market of every row and the filter values (market, sector, tags) before any market data is loaded. Name, exchange and market come from the exchange directory without source requests; the sector needs the company profile, which is a source request.

**Decision:** Each watchlist entry in the profile document is `{symbol, sector, tags}`. `GET /watchlist` returns the entries with ticker, exchange, name and market from the exchange directory; it reads no cached company data and makes no source requests. `PUT /watchlist/{symbol}` stores the sector from the default source's company profile (cached if not expired, otherwise requested; when the request fails or the limit is reached, the sector of an expired cached profile is used; `null` only when no profile is known) and returns the stored item (`200`). When the detailed screen fetches the company profile from the profile's default source, the sector of the entry is updated if the company is in the requesting profile's watchlist. A sector from another source never overwrites it. A watchlist row missing from the directory gets the ticker as its name (supersedes the cached-profile name of ADR-041).

**Consequences:** Watchlists of other profiles keep their sector until their own detailed screen refreshes it. Sectors stay source-specific until they are mapped to a fixed list (beta). A company added while the source fails has no sector until the detailed screen loads its profile.

## ADR-058: Market data lazy-loaded with a status per row

**Date:** 2026-11-15
**Status:** Accepted

**Context:** With ADR-057 the watchlist carries no prices. ADR-055 already refreshed only displayed rows, but returned whole rows without the status of the quote, so the app couldn't tell a failed refresh from cached data.

**Decision:** `GET /watchlist/market-data?symbols=...` replaces `GET /watchlist/refresh` (same symbol rules as ADR-055). It returns `{symbol, status, quote, indicators}` per row: stale quotes are refreshed within the source limits, company profiles are not requested, and indicators are empty until they are implemented. The app keeps the last market data it received per row, across watchlist reloads, and shows it until newer data arrives. Price and indicators are drawn:
- `-` in the default text color while nothing has been received for the row, or when the status is `NO_DATA` or `NOT_SUPPORTED` without a quote;
- in orange (a theme color with light and dark variants) when the last load failed: network error, error response of the backend, `LIMIT_REACHED` or `SOURCE_ERROR`; the value stays the last one received, or `-`;
- otherwise with the usual price coloring and cache indicator.

**Consequences:** The first paint of the watchlist shows `-` in every row until the market data arrives. Red stays reserved for a falling price.

## ADR-059: Search results contain directory data only

**Date:** 2026-11-15
**Status:** Accepted

**Context:** Search rows showed a cached quote and sector when present. Showing prices or sectors for every result would need a quote or profile request per result, at up to one search per second.

**Decision:** Search results contain only directory data (ticker, exchange, name, market, logo) and the watchlist marker; the search reads no cached company data. The response keeps its limit of 25 results. The company profile is requested when a company is added to the watchlist or by the detailed screen.

**Consequences:** One search costs one source request (unless answered from the in-memory search cache). Logos of search rows are still loaded through the logo endpoint.

## ADR-060: Company profiles cached for 90 days

**Date:** 2026-11-15
**Status:** Accepted

**Decision:** Company profile cache entries expire 90 days after they were fetched; the other cached company data keeps the 14-day TTL (ADR-013). The expiry is written into each document's `expireAt` field, which the existing Firestore TTL policy uses, so no infrastructure change is needed. A profile is fetched again when it is older than 90 days (supersedes the 14 days of ADR-032).

**Consequences:** Profiles cached before the change keep their 14-day `expireAt` until they are fetched again.

## ADR-061: User tags only for watchlist companies

**Date:** 2026-11-15
**Status:** Accepted

**Decision:** User tags are stored in the watchlist entry (ADR-057). Only companies in the watchlist can have tags: the tags endpoint returns `400` for other companies, and tag editing on the detailed screen is disabled for them. Removing a company from the watchlist removes its tags.

**Consequences:** Tags for companies outside the watchlist would need a new decision and a separate storage.

## ADR-062: Static exchange table and companies of exchanges without a symbol list

**Date:** 2026-11-15
**Status:** Accepted

**Context:** The Finnhub free plan answers `/stock/symbol` only for the US exchange; for other exchanges, and for quotes and company profiles of their symbols, it answers `403` ("You don't have access to this resource"). Its search still returns European companies (e.g. `AD.AS` with its full name). Without a symbol list, such companies got the exchange code as exchange name and no market, and couldn't be added to the watchlist (ADR-041).

**Decision:**
- A static exchange table maps each known exchange code to exchange name, market, time zone and currency (`AS` -> Euronext Amsterdam, EU, Europe/Amsterdam, EUR). Exchange and market of a symbol come from the symbol list when it contains the symbol, otherwise from the static table.
- An exchange is "not covered" by the source when the source answers that it has no symbol list for it (`NOT_SUPPORTED` or `NO_DATA`). Symbols of a not covered exchange that is in the static table are accepted by `PUT /watchlist/{symbol}` without validation. Symbols missing from a covered exchange's list still return `404`.
- The watchlist entry stores the company name (`{symbol, name, sector, tags}`). It comes from the symbol list when the symbol is listed, otherwise from the optional request body of `PUT /watchlist/{symbol}` (the search result's name, sent by the app), otherwise the ticker. The detailed screen's profile fetch from the default source updates it together with the sector (ADR-057).
- Quotes, company profiles and ratios of symbols of a not covered exchange are returned as `NOT_SUPPORTED` without a source request; the app shows `-` in the neutral color.

**Consequences:** European companies can be found, filtered by market and added, with no sector, price or indicators until a source covering their exchanges is added (beta). Supersedes the `404` for such symbols and the best-effort identity of ADR-041.

## ADR-063: Exchange symbol lists kept for 7 days

**Date:** 2026-11-15
**Status:** Accepted

**Context:** Symbol lists change slowly; downloading them daily costs a source request per exchange per day for little gain. A company listed recently would be missing from an older list and couldn't be added (`404`).

**Decision:** A downloaded symbol list is kept for 7 days (supersedes the daily refresh of ADR-034). When a symbol is not found in a list older than 1 day, the list is downloaded again, at most once per day per exchange. A failed download keeps the old list and is retried after 5 minutes; an exchange the source doesn't cover is asked again after 1 day.

**Consequences:** A new listing becomes visible at the first lookup after its list is 1 day old.

## ADR-064: Source HTTP clients follow redirects

**Date:** 2026-11-15
**Status:** Accepted

**Context:** Finnhub answers `/stock/symbol?exchange=US` with `302 Found` pointing to a pre-signed JSON file on its static file host. The source HTTP client didn't follow redirects, so the download counted as a failed call (outside 2XX), the US symbol list never loaded and every retry spent a request.

**Decision:** The HTTP client shared by all sources follows redirects (Ktor `followRedirects = true`). A redirected request counts as one source call; its outcome is the final response. Redirects from `https` to `http` are not followed (Ktor default).

**Consequences:** Request headers set by a client, such as `X-Finnhub-Token`, may be sent to the redirect target. This is accepted because the sources redirect to their own hosts. If a source redirects elsewhere, the client must handle that redirect explicitly.

## ADR-065: No Finnhub balance sheet for exchanges the free plan doesn't cover

**Date:** 2026-11-15
**Status:** Accepted

**Context:** On the Finnhub free plan, `/stock/financials-reported` answers `403` for symbols of exchanges it doesn't cover (e.g. European exchanges), like quotes and company profiles (ADR-062).

**Decision:** For symbols of exchanges not covered by the source's symbol lists, the Finnhub balance sheet is returned as `NOT_SUPPORTED` without a request, as quotes, ratios and company profiles are. This follows from the free plan currently used; on a plan covering those exchanges, the symbol lists become available and the requests are made again without code changes.


## ADR-066: Backend image built on an E2_HIGHCPU_32 Cloud Build machine

**Date:** 2026-11-15
**Status:** Accepted

**Context:** On the `E2_HIGHCPU_8` machine (8 GB RAM, ADR-023) the native image builder gets a heap of about 6 GB. With the current dependencies (Ktor, Firestore and Cloud Storage clients with gRPC and Netty) the build needs most of it during method compilation; the garbage collector then runs almost continuously, the builder stops reporting progress and its watchdog aborts the build in stage `[6/8] Compiling methods`.

**Decision:** Cloud Build runs the backend image build on an `E2_HIGHCPU_32` machine (32 vCPU, 32 GB RAM). The builder's heap is left at its default size, derived from the machine memory.

**Consequences:** Builds have enough memory headroom and finish faster, but a build minute costs about four times as much as on `E2_HIGHCPU_8`. Local `docker build` needs a Docker VM with at least 12 GB of memory.

## ADR-067: Backend uses a named Firestore database

**Date:** 2026-11-15
**Status:** Accepted

**Context:** The GCP project can host several small Cloud Run services, each with its own data. With all of them in the project's `(default)` Firestore database, their collections, TTL policies and access rights are mixed, and one service's data can't be exported, restored or deleted separately.

**Decision:** The backend stores its data in a named Firestore database, `sixteen-screener` by default (`firestoreDatabase` in `application.conf`, overridable by `FIRESTORE_DATABASE`). The database ID is validated on start. The runtime service account gets `roles/datastore.user` with an IAM condition limiting it to this database. The Firestore emulator tests use a named database too.

**Consequences:** The backend's data is isolated from other services and can be managed on its own. Firestore's free quota applies only to the first database created in a project; if the `(default)` database was created first, usage of the named database is billed from the first operation. Creating a named database requires a project with billing enabled.

## ADR-068: Curated logos only from logoapi.sh

**Date:** 2026-11-17
**Status:** Accepted

**Context:** logoapi.sh returns curated logos (icons and wordmarks) and, when it has none, an auto-discovered favicon of the company website: a small raster image wrapped in SVG. The kind of a returned logo is reported in response headers: `X-Logo-Fallback: wordmark` marks a curated wordmark returned instead of an icon, `X-Logo-Fallback: favicon` marks an auto-discovered favicon. ADR-038 treated any response with `X-Logo-Fallback` as a miss, which rejected the curated wordmarks of most companies.

**Decision:** Only curated logos are taken from logoapi.sh. Requests keep `fallback=404`, so logoapi.sh answers `404` instead of a favicon; a response with `X-Logo-Fallback: favicon` is still treated as a miss. Wordmarks and icons are accepted. This replaces the `X-Logo-Fallback` rule of ADR-038. The rule applies to logoapi.sh only; other logo sources may be added later with their own rules for raster logos or favicons.

**Consequences:** Most companies get their real logo; wordmarks are wider than icons and are shown scaled down in the square logo slot. Companies with only a favicon on logoapi.sh get the default logo. Misses recorded under the old rule keep the default logo until they expire (14 days) or are deleted from the `logoMisses` collection.

## ADR-069: Finnhub PNG logos as a backup logo source

**Date:** 2026-11-17
**Status:** Accepted

**Context:** logoapi.sh has no curated logo for some companies (ADR-068). Finnhub publishes company logos as PNG files on static2.finnhub.io; the URL is the `logo` field of the `profile2` response and follows the pattern `.../stock_logo/{ticker}.png` (full symbol with suffix for non-US exchanges). Downloading these files is not a Finnhub API call.

**Decision:** "Finnhub logos" (`finnhub-logos`) is a separate logo source used as a backup after logoapi.sh. The URL comes from the cached Finnhub company profile, otherwise from the pattern; the Finnhub API is never called for logos. Downloads are limited to 1 MB and must be PNG files. PNG logos are stored in the bucket under `finnhub-logos/` and served as `image/png`; the backend does no conversion. The source has no request limits and a never-resetting total call counter shown on the Sources screen (`requestCounters/{source}__TOTAL`, without TTL). Misses are recorded per logo source and symbol (`logoMisses/{source}__{symbol}`). The default logo is cached by clients for 1 day when no source has a logo and 1 hour after a source failure, and the app respects the backend's Cache-Control.

**Consequences:** More companies get a real logo, including non-US companies. The app renders both SVG and PNG logos; small PNG logos are drawn at their own size. Calls to static2.finnhub.io don't use the Finnhub API limit; the pattern is undocumented and may change, in which case misses are recorded and the default logo is returned.

## ADR-070: Exchanges table served from the static exchange table

**Date:** 2026-11-17
**Status:** Accepted

**Context:** The Exchanges screen lists the exchanges the app knows about. The backend already has a hardcoded static exchange table (ADR-062) mapping symbol suffixes and MICs to exchange name, market, time zone and currency.

**Decision:** The static exchange table is extended with full exchange names and stays the single hardcoded table. `GET /exchanges` (not per profile) returns one row per known symbol suffix (suffixes sharing an exchange give separate rows) and one row per US exchange MIC with an empty suffix: suffix, short name, full name, MIC, currency, time zone, market. MICs that are neither US nor the target of a suffix are not listed. The app requests the table the first time the screen is opened, keeps it in memory only (not persisted, lost on app shutdown) and filters it in memory by a case-insensitive substring match in any field except the market flag.

**Consequences:** No source requests and no storage are involved. Changing the table requires a backend release until it is moved to a file or the database (RC).

## ADR-071: Indicator intervals as three thresholds

**Date:** 2026-11-17
**Status:** Accepted

**Context:** The spec described indicator intervals as lists of ranges per level (bad, warning, good), which may overlap or leave gaps and are hard to edit on a phone. Some ratios are not monotonic: a P/E is bad below 0 and above 40, good between 0 and 15.

**Decision:** A ratio setting has three thresholds `t1 <= t2 <= t3` splitting `[0, +inf)` into four intervals, a level (`GOOD`, `WARNING`, `NEUTRAL`, `BAD`) per interval, a separate level for values below 0 and an on/off switch. Lower bounds are inclusive. Each ratio has a fixed scale maximum and step (not editable in the app); values above the scale maximum take the level of the last interval. The settings page draws each ratio as a bar from 0 to the scale maximum with a below-zero zone at the left edge: thresholds are dragged (snapping to the step, exact values typed after a long press) and a tap on an interval cycles its color. The `Range` schema is removed from the spec.

**Consequences:** Intervals never overlap and have no gaps. Levels may repeat, and equal thresholds remove an interval, so fewer than four intervals can be configured. Values below 0 are red by default but can be recolored per ratio (growth, beta).

## ADR-072: Default indicator settings copied into the profile on first access

**Date:** 2026-11-17
**Status:** Accepted

**Context:** Profiles created before indicators exist have no indicator settings. The defaults should be tunable without a backend release.

**Decision:** Defaults live in the Firestore document `config/indicatorDefaults`, created by the backend from values in code when missing and never overwritten. Reading the settings of a profile without settings (from the settings endpoint or to calculate indicators) copies the defaults into the profile document in a transaction. Ratios that are in the defaults but missing in the profile are added on read in the same way; existing ratio settings of a profile are never changed. Defaults: all 31 indicator ratios have values, and only P/E, ROA and ROE are switched on.

**Consequences:** Existing profiles get indicators without a migration. Later changes of the defaults reach only profiles created or reset (RC) afterwards; newly added ratios reach all profiles. A `GET` can write to Firestore once per profile.

## ADR-073: Order of displayed indicators

**Date:** 2026-11-17
**Status:** Accepted

**Context:** The company row has room for three indicators. The most useful three are a mix of strong and weak points.

**Decision:** The backend returns indicators in display order: one per level in the order green, yellow, red (the first in settings order within a level); free places go to the biggest group, on a tie to the group whose next indicator comes first in settings order; the chosen three are grouped by level in the same order, followed by the remaining indicators. The app shows the first three under each other to the right of the price and `...` after the third when there are more. Settings order is the order of the defaults, as settings can't be reordered yet.

**Consequences:** The app does no selection logic, and the detailed screen uses the same list for coloring its ratios.

## ADR-074: Curated Finnhub ratios and calculated P/E

**Date:** 2026-11-17
**Status:** Accepted

**Context:** Finnhub `/stock/metric` returns about 130 keys, many of them absolute values (52-week high, market cap) or the same ratio for several periods. Recorded responses (AAPL, JPM, RIVN, MCD, BRK.B) show that Finnhub omits P/E, EPS growth, P/CF and P/FCF for companies with losses instead of returning negative values, that banks have no current ratio, turnover or gross margin values, and that `BRK.B` is answered with `BRK.A` data.

**Decision:** The backend maps 33 ratios: 31 that can be indicators (valuation, profitability, margins, growth, liquidity, leverage, efficiency, dividends, risk) and 2 per share values (EPS, book value per share) shown on the detailed screen only. Each ratio takes the first present value among its keys, TTM or quarterly first, then annual. Units stay as Finnhub returns them (percents for returns, margins, growth, yield and payout). Per share values are dropped when the response symbol differs from the requested one. When Finnhub returns no P/E and the EPS is negative, the P/E used for indicators is price / EPS from the quote of the same source; the detailed screen shows the source value (calculated values on the detailed screen are a future improvement).

**Consequences:** Companies with losses get a red P/E indicator. Missing ratios show `-` and make no indicator.

## ADR-075: Ratios refreshed daily for watchlist indicators

**Date:** 2026-11-17
**Status:** Accepted

**Context:** Indicators of watchlist rows need ratios, an extra Finnhub call per company. Ratios change slowly; the 1-hour freshness of quotes would double the number of calls.

**Decision:** `GET /watchlist/market-data` also returns indicators, from cached ratios refreshed when older than 1 day, within the same per-source budget reservation as the quotes (quotes first). Each item has a separate `indicatorsStatus`; the app draws indicators in orange only when the ratios failed to load (network or backend error, `LIMIT_REACHED`, `SOURCE_ERROR`), independently of the price.

**Consequences:** Watchlist indicators may be up to 1 day old. The detailed screen keeps its rules (cached data on open, manual refresh).

## ADR-076: Watchlist cached on the device and reconciled with a numbered change log

**Date:** 2026-11-17
**Status:** Accepted

**Context:** The watchlist was kept in memory only, so after a cold start the screen stayed empty until `GET /watchlist` returned, and showed nothing when the backend could not be reached. A slow `GET /watchlist` could also overwrite a company added or removed while it was in flight, because the response replaced the list in the app. The app cannot tell whether the backend handled a `PUT` or `DELETE` before or after it read the watchlist for a `GET` that overlapped it.

**Decision:** The app stores the watchlist entries of each profile on the device (a DataStore file, JSON per profile ID) and shows them as soon as the profile is known; a `GET /watchlist` then runs in the background and replaces them. Prices and indicators are not stored and are lazy-loaded as before (ADR-058). The stored list holds only changes confirmed by the backend, never pending ones. The cache of a profile is deleted when the backend answers `404` for it.
Adds and removes get increasing numbers and go into a change log; a confirmed change also gets the number current at its confirmation, and a failed change leaves the log (its rollback). The displayed list is the last backend snapshot (or the cached list) with the log replayed in order; replaying is idempotent (add only when absent, remove only when present), so a change already contained in the snapshot does no harm. Each `GET /watchlist` remembers the number current when it is sent: its snapshot prunes changes confirmed before that, keeps the others and replays them. A snapshot older than the last applied one is ignored. When changes are left in the log after a snapshot, the app requests the watchlist once more after the last pending change settles, so the list ends up matching the backend (order, fields filled by the backend, changes from other devices).
The first snapshot replacing a cached list does not reload market data of rows already loaded since the start.

**Consequences:** The watchlist is shown at once on a cold start and stays visible offline. A change during a load costs one extra `GET /watchlist` (no source requests, ADR-057). Until the snapshot arrives, the list may differ from the backend by changes made on other devices.

## ADR-077: Build number from the git commit count

**Date:** 2026-11-17
**Status:** Accepted

**Context:** `versionCode` was fixed at 1, so every APK looked like the same build to Android, and the settings screen showed only the hand-maintained version name. The build number must increase automatically without the build writing to a file or committing, and must not depend on the machine that builds.

**Decision:** Gradle calculates the build number on every build with `git rev-list --count --first-parent HEAD`: only commits on `main`'s own line count, so commits of squashed or deleted branches are never included and a merge counts once. It is used as `versionCode` and compiled into `BuildConfig` together with the short commit hash and a flag for uncommitted changes; nothing is stored in a file. `versionName` stays a semantic version raised by hand. The settings screen shows `<version> (build <number>, <hash>)`, with `-dirty` after the hash when the working tree had uncommitted changes. The build fails outside a git checkout.

**Consequences:** Any machine builds the same number from the same commit, and the number identifies the code. Releases must be built from `main`; rewriting commits of `main` that were already built, or building another branch, can repeat or lower a number. A CI checkout must fetch the full history. `adb install -r` refuses a lower build number, which guards against installing an older APK by mistake.

## ADR-078: Exchange short names of at most 6 characters

**Date:** 2026-11-18
**Status:** Accepted

**Context:** The exchange short name of the static exchange table (ADR-062) is shown under the ticker on the company rows of the app and returned as `shortName` by `GET /exchanges`. Names like "Euronext Amsterdam" or "Nasdaq Stockholm" do not fit there.

**Decision:** Exchange short names are our own hardcoded values in the static exchange table, at most 6 characters long. The city name is used when it fits (Paris, Milan, Oslo, Dublin), otherwise the usual abbreviation of the exchange (AMS, STO, BET, BIST). Frankfurt is FSE rather than FRA, because FRA is the ISO code of France. MICs of the same exchange may share a short name (NASDAQ, OTC, SIX, Dublin, Milan); a short name shared by several MICs always has one time zone and currency. Full exchange names stay unchanged and are shown on the Exchanges screen.

**Consequences:** The short name fits under the ticker without truncation. A test fails when a short name is longer than 6 characters or is shared by exchanges of different time zones or currencies.

## ADR-079: Stocks told from other securities, and the home exchange

**Date:** 2026-11-18
**Status:** Accepted

**Context:** A search for one company returns many listings: the same share quoted on foreign exchanges in other currencies (Coca-Cola on NYSE, BMV and Vienna), depositary receipts of foreign companies on US exchanges (Novo Nordisk `NVO`, SAP `SAP` on NYSE), preference shares and funds. The user cannot see which rows are stocks, nor which one trades on the company's own market. Nothing should be hidden: the user may want to look at an ADR on purpose. Finnhub has no reliable primary-exchange field: `profile2.exchange` is free text whose wording changes, ISIN is only available with a paid entitlement and not for US symbols, and FIGI grouping depends on data that is often missing.

**Decision:** The security type comes from the exchange directory (`/stock/symbol` `type`, OpenFIGI vocabulary), and for a search result not listed there from the search itself. The backend classifies it and returns `type` and `stock` with every company (search, watchlist, company profile): a type is a stock when it is "Common Stock" or "REIT" (case-insensitive, trimmed) or unknown; every other type (ADR, GDR, NY Reg Shrs, Preference, ETP, funds, ...) is not. The app draws the name of a non-stock in a pale lavender color, which is not used for anything else (red, green, orange and yellow already have a meaning); no text is added to the rows.
The static exchange table (ADR-062) gets an ISO 3166-1 alpha-2 country and a main-venue flag per exchange. A main venue is an exchange where domestic companies have their primary listing; the German regional exchanges (Frankfurt, Berlin, Munich, Stuttgart, Dusseldorf, Hamburg, Hanover) and the US OTC markets are not, every other exchange is. The company profile response gets `homeExchange`: true when the security is a stock, its exchange is a main venue and the exchange country equals the company's country from the profile of the selected source. The detailed screen shows `type: <type>` under the header (red for a non-stock) followed by a checkmark and "home exchange" when `homeExchange` is true. The mark is shown on the detailed screen only: on the search screen it would need one company profile request per result. The detailed screen requests the company profile as soon as it opens, whatever tab is selected, and shows the mark when it arrives; the backend serves the profile from its cache (90 days, shared by all profiles), so the source is called only for the first request of a company.

**Consequences:** ADRs, preference shares and funds are recognisable at a glance, and a foreign quotation of a stock (Apple on Xetra, Coca-Cola on Vienna) gets no home-exchange mark. The mark is an approximation: a US stock can be marked on two US exchanges, and a company listed outside its country of incorporation gets no mark at all (Ferrari and Stellantis are Dutch companies listed in Milan and New York; Linde is Irish and listed in New York). If a source reports the country of the quoting venue instead of the issuer for a foreign quotation, that quotation is marked wrongly. A source without a country gives no mark. New exchanges in the static table need a country and a main-venue flag.

## ADR-080: Exchange symbol lists only for exchanges covered by the plan

**Date:** 2026-11-19
**Status:** Accepted

**Context:** A search for a company name returns listings on many exchanges. The backend downloaded the symbol list of every exchange it had not seen yet, one `/stock/symbol` request per exchange. The Finnhub free plan answers only the US list and `403` for every other exchange, and that "not covered" answer was kept in memory only, so every Cloud Run cold start spent the requests again. One search could spend 15 to 25 of the 60 requests per minute.

**Decision:** A source gets an optional `directoryExchanges` setting: the exchange codes whose symbol lists the source is asked for (`[US]` for Finnhub). Every other exchange is not covered by the source (ADR-062) without a request. Without the setting, the symbol list of any exchange is requested, as before. The app is unaware of it: it requests data as usual and the backend answers `NOT_SUPPORTED` at once for symbols of not covered exchanges.

**Consequences:** Search and watchlist spend no requests on exchanges the plan does not cover, also after a cold start. A paid plan or a new source that covers more exchanges needs its list extended in the configuration.

## ADR-081: At most 10 parallel requests per source

**Date:** 2026-11-19
**Status:** Accepted

**Context:** Besides 60 requests per minute, Finnhub rejects more than 30 requests per second on every plan. Symbol list downloads ran in parallel without a bound, and a `429` sets the request counters to their limits for the rest of the minute.

**Decision:** Each backend instance sends at most 10 requests at a time to the same source, whatever the request (search, quote, ratios, profile, balance sheet, symbol list, logo). Further requests wait for a free slot; time spent waiting does not count toward the request timeout.

**Consequences:** Bursts of requests are spread out. The bound applies per instance, so several Cloud Run instances can together send more. It is not a per-second limit: fast responses can still exceed 30 requests per second, which is unlikely with the current call patterns.

## ADR-082: Search sent after a pause in typing

**Date:** 2026-11-19
**Status:** Accepted

**Context:** The search sent a request on the first letter and then at most one per second (ADR-054), so typing a company name spent 4 to 6 source requests, and the results jumped through every intermediate query.

**Decision:** The app sends a search request only when no letter was typed for 500 ms. The keyboard's search action sends the current query at once. An empty input (after trimming) clears the results at once without a request. A newer request still cancels the one in flight. The rest of ADR-054 (query length, ticker-only matching of 1- and 2-character queries, 25 results) is unchanged.

**Consequences:** Typing a name usually costs one or two requests. Results appear 500 ms after the last letter instead of while typing. A cancelled request may still have spent a source request on the backend.

## ADR-083: Source roles and manual-only sources

**Date:** 2026-11-19
**Status:** Accepted

**Context:** The free plans of the additional sources (`docs/api/FREE-TIER-COMPARISON.md`) differ in coverage as much as in limits: Finnhub has no European data, and EODHD is the only free source of European prices and dividends but allows 20 requests per day. One default source per profile can't serve both markets, the sources can't replace each other, and automatic requests (watchlist refresh, data loaded when nothing is cached) would spend the EODHD day in a few seconds.

**Decision:**
- Sources serve roles: `SEARCH`, `US` (companies of US exchanges) and `INTERNATIONAL` (companies of any other exchange, EU or not). Each source is configured with the roles it can serve, and a default source per role: Finnhub for `SEARCH` and `US`, EODHD for `INTERNATIONAL`. The global `defaultSource` setting and the single `Profile.defaultSource` are replaced by `defaultSources` per role.
- In beta every profile uses the configured defaults; there is nothing to switch between. Storing defaults per profile and switching them on the Sources screen (limited to the sources configured for the role) moves to RC.
- The app is unaware of the roles. The backend picks the source of the company's role; a source not configured for that role answers `NOT_SUPPORTED` without a request (SEC EDGAR stays an additional balance sheet source for US companies). The source dropdown of the detailed screen lists only sources configured for the company's role.
- Each source has an `automaticRequests` setting, on for Finnhub only. Company data requests carry `trigger=AUTO|MANUAL` (replacing `refresh`); `MANUAL` is sent only by the refresh button of the detailed screen. A source with automatic requests off is never called for `AUTO`: stale cached data is returned as is, and without cached data the new status `NOT_REQUESTED` with empty data (shown as '-' in the neutral color, with a hint to refresh on the detailed screen). A `MANUAL` request calls it only when nothing is cached or the cache is older than 1 hour.
- EODHD symbols are built from the EODHD exchange code kept per suffix in the static exchange table (`SAP.DE` is `SAP.XETRA`), shown next to the MIC on the Exchanges screen. EODHD downloads no symbol lists, and its `402` (daily limit reached) is handled like `429`.
- FMP, Tiingo and Alpha Vantage, the source comparison and the switching of defaults move to RC.

**Consequences:** International companies get prices and dividends, but no ratios, indicators, profiles or balance sheets on the free plans. Their watchlist prices change only after a manual refresh on the detailed screen (the cache is shared between users). US companies get no dividends until a US dividends source is added. A source routing per market and data type (e.g. dividends of US companies from another source) is not supported; the configuration has one source per role.
