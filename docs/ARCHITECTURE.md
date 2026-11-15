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
Watchlist filter selections are kept in memory while the app is running and are not stored on the device.
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
Database: Firestore. Cached data is stored with Firestore TTL feature to be automatically removed.
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
The watchlist is stored in the profile as a list of entries `{symbol, name, sector, tags}` in insertion order. The name is stored when the company is added: from the exchange directory when the symbol is listed there, otherwise the name the app sends from the search result, otherwise the ticker. The watchlist endpoint returns these entries with ticker, exchange and market resolved from the exchange directory (or the static exchange table, see Markets); it reads no cached company data and makes no source requests. The sector is taken from the company profile of the default source when the company is added (the cached profile if not expired, otherwise requested; when the request fails, the sector of an expired cached profile; `null` only when no profile is known) and updated, together with the name, whenever the detailed screen fetches the company profile from the profile's default source and the company is in the requesting profile's watchlist. Watchlists of other profiles are not updated.
Prices and indicators (market data) are loaded with a separate endpoint taking the symbols (at most 50 per request), which refreshes stale quotes within the source limits and returns a status per row. The app calls it for the rows it displays plus a 5-row buffer below them: when the watchlist is shown, when new rows are scrolled into view (each row once per loaded watchlist) and when a filter changes (all displayed rows plus the buffer). The app keeps the market data it has received across watchlist reloads and shows it until newer data arrives.
Search results contain directory data only; the search reads no cached company data.
Watchlist, user tags, default source and indicator settings are per-user info, stored with the user profile and used for the user it belongs to. User tags are stored in the watchlist entries, so only watchlist companies have tags.
Watchlist filtering doesn't require any special database feature (like search or index). A watchlist holds at most 250 companies. All the filtering can be done in-memory (either frontend or backend).

## Source calls and failures
Source HTTP clients follow redirects (e.g. Finnhub serves exchange symbol lists through a redirect to a file host); a redirected request counts as one call.
Detailed screen endpoints return cached data as is; when nothing is cached, the data is requested from the source automatically (within its request limits). A manual refresh requests the source even if the cache is fresh.
If a source call fails (response outside 2XX, timeout, unexpected application error) or the source request limit is reached, the backend returns the latest cached data with a status describing the reason. Without cached data it returns the same status with empty data (null fields, empty lists), which the frontend displays as '-'. Source failures never turn into an error response of the backend.
Search results are not cached in Firestore: when the limit is reached or the source call fails, search returns an empty list with a status (`LIMIT_REACHED`, `SOURCE_ERROR`), and the frontend shows the reason under the search box.
The outcome of the last call to each source is stored in Firestore (shared between instances) and returned by the sources endpoint: success, HTTP status code, timeout (no response received, including connection failures) or unexpected application error. The frontend displays it as a connection indicator: nothing, the code, 'T' or 'NPE'. A request skipped because of a reached limit is not a call and doesn't change the outcome.

## Company logos
Being downloaded, logos are stored in a GCS bucket.
Backend tries first to find a logo in the bucket, if not found - download it from an external source and save into the bucket.
If not found in the source, backend returns default logo image.
Logos are stored and served in SVG format as provided by the source, with a long-lived Cache-Control header; the backend does no image conversion.
The frontend renders SVG logos with Coil and its SVG decoder (coil-svg). If a logo can't be decoded, the frontend shows the default logo.

## Indicators
Indicator settings (intervals per ratio and on/off switch) are stored in Firestore per profile.
Indicators are calculated on the backend and returned together with company data; the frontend only renders them.

## Markets
Market is detected by exchange. US exchanges map to the USA market.
EU exchanges, together with UK (LSE) and Swiss (SIX) exchanges, map to the EU market (EU flag and filter).
Exchanges outside both markets have no market: such companies get no flag and are shown only with the 'all' market filter.
Exchange data (symbol lists per exchange with exchange code, name and currency) is downloaded from the source when first needed and kept for 7 days, saved as a file in the GCS bucket and kept in memory. A symbol missing from a list older than 1 day triggers one new download of that list (at most once per day per exchange). It is used to resolve the exchange name and market of search results and watchlist rows without extra source requests, and to validate symbols. The exchange of a symbol is taken from its suffix (`SAP.DE`, `SHEL.L`; no suffix means US).
A static exchange table maps each known exchange code to its exchange name, market, time zone and currency (`AS` is Euronext Amsterdam, EU, Europe/Amsterdam, EUR). It is the fallback when the source provides no symbol list for an exchange: the Finnhub free plan provides the US list only. Symbols of such exchanges (not covered by the source) are accepted without validation when added to the watchlist, with the name from the search result; the source is not asked for their quotes, company profiles, ratios or balance sheets, which are returned as `NOT_SUPPORTED` without a request (a consequence of the Finnhub free plan).

## Source data
Each source's data is displayed as provided by that source, without cross-source normalisation.
For Finnhub (free plan) the balance sheet comes from reported financials, i.e. raw data as filed with SEC EDGAR.
SEC EDGAR is kept as a separate source to double-check balance sheets from other sources.
