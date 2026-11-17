## Description
This is a mobile application with a cloud backend. Application is a stock screener, provides info for selected stocks and allows to search stocks by filters.
Backend works as an accumulator of data from different sources and caches collected data.

## Sources
Backend uses several external sources to gather info from.
- Company logo is being downloaded from logo source and cached in a file storage (see ARCHITECTURE.md)
- Company stock price and ratios are downloaded from mentioned sources. One of those sources is a 'default' one, and user can switch it on sources screen. Data from all sources is being gathered and displayed on a comparison screen.
- In addition to common sources (providing prices + ratios + balance sheet) there is a "source of truth" balance sheet loaded on demand on the comparison screen. Balance sheet displays annual data only (quarterly reports are not requested or displayed).

### USA stocks financial info
US SEC EDGAR (data.sec.gov). Source of truth for american stock's balance sheet and other company data.
Doesn't contain prices and ratio.

### Europe stocks financial info
No source yet. EU plans to create such a source in 2027-2029.
Consider it as a future improvement.

### All stock prices and ratios
Financial Modeling Prep (FMP), ~250 calls/day
Finnhub.io, 60 calls/minute
Tiingo, 50 calls/hour (up to 1,000/day, max 500 unique symbols/mo)
Alpha Vantage, 25 calls/day (5 calls/min)
EODHD, 20 calls/day

### Company logos
Download company logos in svg format from logoapi.sh
Example:
~~~
https://logoapi.sh/ticker/AAPL.svg
~~~

## Application screens
There are 4 tabs on the main screen of the app:
- Watchlist. Displays selected companies. There are buttons to add stock and remove any existing one.
Contains filters (drop-down controls): Market (USA/EU/all), sector (finances/technology/etc), user tags.
- Search. Contains input box for search; searches default source by company ticker or first letters from company name.
Search is triggered after each letter typed, but no more than 1 request/sec.
Companies found displayed as a list of rows without stock price and indicators (logo, ticker with exchange name, company name, flag); any company can be added to the watchlist. Search makes no source requests other than the search itself.
If the source request limit is reached or the source fails, the search shows no results and the reason under the input box.
- Exchanges. A table of known exchanges, with a text filter above it that matches any field.
- Sources. Displays all the sources for stock prices and ratios. Default one is marked and can be changed by user.
Each source shows its request limits and counter for requests already made per corresponding interval (min, hour, day).
Each source also shows a connection indicator for the last call made to it: nothing if the last call succeeded; the response code (e.g. 400) if the response was outside 2XX; 'T' for a timeout; 'NPE' for an unexpected application error.
On the watchlist and search screens, each company has a button to display full info. That button opens a new 'detailed', or 'comparison' screen.
That screen contains several tabs:
- Price and ratio,
- Dividends,
- Balance sheet (annual data only; a table with one column per year, scrolled horizontally when the years don't fit),
- Company profile.
Each tab displays data from the 'default' source. Each tab contains a dropdown to select the source (balance sheet contains additionally government source, the 'source of truth'). On company profile tab user can append its own tags (for filtering on the Watchlist screen). Tags belong to the watchlist: only companies in the watchlist can have tags (tag editing is disabled for other companies), and removing a company from the watchlist removes its tags.
There should be one more 'settings' screen, accessible by 'settings' button, with indicators and other app settings.

## Displaying company info
Each company is displayed as a row with next elements:
Logo image, ticker, Company name, Flag icon (USA/Europe), Stock price, indicators (search results show no stock price and indicators)
Ticker is displayed together with a short Exchange name. Exchange name goes in a much smaller font (and weaker color) under the ticker itself.
Company name takes no more than half of the company card's width. If it's longer, tail letters are dropped and replaced with dots (display only) to get half of the width.
Flag icon is an Unicode character going right after the company name, displaying the market (USA flag for American markets and EU flag for European markets).
Companies from exchanges outside these markets have no flag and are shown only when the market filter is 'all'.
Stock price is displayed in different colors:
- green, if current price is bigger than previous cached price;
- red, if current price is lower than previous cahced price;
- default text color (white in the dark theme, black in the light theme), if equals or previous data is absent;
- orange, if the last attempt to load it failed (network error, backend error, source error or source limit reached); the last value the app has is still shown, or '-' if there is none.
By 'previous cached price' we consider latest price for yesterday (or days before), excluding today's data.
Under the stock price lays caching indicator - an Unicode character displaying 'clocks' symbol + number of days.
For today's data clock number is 1; for yesterday's data it's 2, and so on. If data is fresh (or cached less than 1 hour ago),
indicator is not displayed.
Cache indicator displayed in smaller font and pale color, same as an exchange name.
On the watchlist screen, stock price and indicators are loaded lazily for the displayed rows (see Data freshness). Until a row's data arrives, the price and indicators show the last values the app has, or '-' in the default text color if it has none. A source that has no price for the company (or doesn't provide prices) shows '-' in the default text color as well. Indicators follow the same rules as the stock price, but are orange only when the load of the ratios they come from failed.

### Indicators
Indicators are special markers showing company's weakest and strongest points. Literally it's ratio names (P/E, ROE etc) displayed in different colors if they sit in a configured intervals:
- bad value: red,
- not-so-good value: yellow,
- average/expected value: don't display indicator,
- better than average: green.
Each ratio can be configured with those 4 intervals (bad, warning, ok, good) and can be switched on and off (as an indicator) on the indicator settings page.
Indicators sit to the right of the stock price, one under another. At most 3 are shown, followed by '...' when there are more.
The 3 shown are one of each color in the order green, yellow, red; the first in settings order is taken within a color. Places of missing colors go to the biggest color group (on a tie, to the group whose next indicator comes first in settings order).
On the detailed screen, ratios that make an indicator are shown in the indicator's color.

## Users and profiles
This app has a limited amount of users (friends and family). It is not designed for commercial use nor for a high load.
All company data can be cached and shared between users.
Watchlist and user tags are per-user data and should be stored individually per user (profile).
When mobile app first starts, it asks for a profile name, which is stored and used on next runs (without asking again).
Profile name can be switched in settings at any time (so the user can get access to other user data - no protection or security needed).

## Data freshness
If company's data has been collected less than an hour ago, it's considered fresh data and doesn't require a refresh.
The watchlist itself (companies with their sector and tags) is loaded without any source requests. Stock prices and indicators are loaded separately, only for the displayed companies.
If data collected more than an hour ago, it needs a refresh for a watchlist screen. Only the displayed companies are refreshed, plus 5 more below them as a buffer for scrolling: rows scrolled into view are refreshed as they appear, and a filter change refreshes all displayed rows (plus the buffer). Companies that are not displayed keep the data the app already has. If our source ran out of request limit, we don't refresh data and show latest cached data only.
For the 'detailed' screen we don't make a separate request on the screen opening - just display the existing data. If there is no cached data at all, it is requested from the source automatically. At the same time, that screen has a refresh button to be triggered manually at any time user decides to refresh; it refreshes all tabs.
If a source returns an error, times out or its request limit is reached, the latest cached data is shown. If there is no cached data, the data fields are shown as '-'.
