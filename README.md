# Sixteen Screener

A stock screener consisting of an Android application and a cloud backend.
The app shows key information about selected stocks, highlights their strongest and weakest ratios,
and lets you search for companies and compare their data across several financial data providers.

This application is created by AI.

## Components

| Folder     | Description                                                                 |
|------------|-----------------------------------------------------------------------------|
| `frontend` | Android application (Kotlin)                                                |
| `backend`  | Kotlin service deployed to Google Cloud Run; aggregates and caches data     |
| `docs`     | Requirements, architecture, feature list, decision log and API references   |

## How it works

- The backend collects prices, ratios, dividends, balance sheets and company profiles from multiple
  external providers (Finnhub, Financial Modeling Prep, Tiingo, Alpha Vantage, EODHD) and the
  US SEC EDGAR as the source of truth for US balance sheets.
- Collected data is cached in Firestore and shared between users, keeping usage within the
  providers' free request limits. Company logos are cached in a Cloud Storage bucket.
- Each user has a profile with a personal watchlist and tags.

## Main features

- Watchlist with filters by market, sector and user tags
- Company search by ticker or name
- Detailed company view with per-source comparison of prices, ratios, dividends and balance sheets
- Configurable ratio indicators that mark good and bad values
- Source overview with request limits and usage counters

## Build and deploy

See `backend/README.md` and `frontend/README.md` for build and deployment instructions,
and `backend/SETUP.md` / `frontend/SETUP.md` for one-time configuration.
