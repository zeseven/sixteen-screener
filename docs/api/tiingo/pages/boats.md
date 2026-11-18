# Real-time & Historical BOATS Overnight API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-boats`).

**REST Endpoints**

Beta

The BOATS Overnight Real-time API Endpoints are currently in beta. For production use cases, we recommend the IEX endpoints - which these endpoints expand upon.

Access requires the **BOATS Real-time** entitlement — activate it on the Entitlements page.

```
# To request top-of-book/last for all tickers, use the following REST endpoint
https://api.tiingo.com/boats

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/boats/<ticker>

# Historical Overnight Intraday Prices
https://api.tiingo.com/boats/<ticker>/prices?startDate=2026-07-22&resampleFreq=5min
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.7.1 Overview

Tiingo's BOATS Overnight Real-time API exposes Blue Ocean ATS (BOATS) equity market data over REST. BOATS is the dominant overnight US equity venue. With a Tiingo BOATS entitlement you get venue-native top-of-book and last-sale snapshots, plus overnight intraday OHLC history.

Benefits of Tiingo + BOATS

- 12,000+ US equities quotable on Blue Ocean ATS (BOATS).
- Data includes Top-of-Book (Bid/Ask/Mid) and Last Sale (trade) data.
- Overnight intraday OHLC bar history for the BOATS session (8:00 PM–3:59 AM ET).
- Tiingo enriches the data with convenience fields such as mid and tngoLast.
- Quotes and trades updated in real time.
- Data is served via a REST API and a Websocket firehose API.

Access to the BOATS REST API requires the **BOATS Real-time** entitlement on your API account. Without that entitlement, requests to `https://api.tiingo.com/boats` are rejected.

Historical bars are limited to the overnight BOATS session (**8:00 PM through 3:59 AM Eastern**). The `afterHours` request parameter is accepted for compatibility with other equity intraday endpoints, but it does not widen this overnight window.

For the full tick firehose (every validated quote and trade), see 3.5 Websockets - BOATS Overnight Real-time.

You can find out about the full product offering on the Product - BOATS page.

### 2.7.2 Current Top-of-Book & Last Price

**To request top-of-book and last price data for a stock, use the following REST endpoints.**

```
# To request top-of-book/last for all tickers, use the following REST endpoint
https://api.tiingo.com/boats

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/boats/<ticker>
```

**Response**

With a valid BOATS Real-time entitlement, snapshot responses include live bid/ask, last/lastSize, and volume. Derived LQ (liquidity-quote) fields from other Tiingo equity feeds are not returned on this endpoint.

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `timestamp` | Timestamp | datetime | The timestamp the data was last refreshed on. |
| `quoteTimestamp` | Quote Timestamp | datetime | The timestamp of the last quote (bid/ask) update from BOATS. |
| `lastSaleTimestamp` | Last Sale Timestamp | datetime | The timestamp of the last trade (last/lastSize) update from BOATS. |
| `last` | last | float | Last is the last trade that was executed on BOATS. |
| `lastSize` | Last Size | int32 | The amount of shares traded (volume) at the last price on BOATS. |
| `tngoLast` | Tiingo Last | float | Tiingo Last is either the last price or mid price. The mid price is only used if our algo determines it is a good proxy for the last price. So if the spread is considered wide by our algo, we do not use it. Also, after the official exchange print comes in, this value changes to that value. This value is calculated by Tiingo and not provided by BOATS. |
| `prevClose` | Previous Close | float | The official closing price (closing auction) taken from Tiingo's end-of-day prices (adjClose), so it is adjusted for splits and dividends. prevDate gives the trading date it belongs to. Before 8:00pm ET this is the previous trading day's close; at 8:00pm ET, when the overnight session starts, it switches to the current day's close. If the official close is not available yet at 8:00pm ET, this is temporarily the last price traded before 8:00pm ET and prevDate is null. This value is provided by Tiingo and not by BOATS. |
| `prevDate` | Previous Close Date | date | The trading date (YYYY-MM-DD) of the official close reported in prevClose. Null when prevClose is a temporary last traded price because the official close is not available yet. |
| `open` | Open | float | The opening price of the asset on the current day. This value is calculated by Tiingo and not provided by BOATS. |
| `high` | High | float | The high price of the asset on the current day. This value is calculated by Tiingo and not provided by BOATS. |
| `low` | Low | float | The low price of the asset on the current day. This value is calculated by Tiingo and not provided by BOATS. |
| `mid` | Mid | float | The mid price of the current timestamp when both "bidPrice" and "askPrice" are not-null. In mathematical terms: mid = (bidPrice + askPrice)/2.0 This value is calculated by Tiingo and not provided by BOATS. |
| `volume` | Volume | int64 | Live BOATS session volume for the ticker. Volume is never delayed on the BOATS REST endpoint. |
| `bidSize` | Bid Size | float | The amount of shares at the bid price on BOATS. |
| `bidPrice` | Bid Price | float | The current bid price on BOATS. |
| `askSize` | Ask Size | float | The amount of shares at the ask price on BOATS. |
| `askPrice` | Ask Price | float | The current ask price on BOATS. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `tickers` | Ticker | no | string | Ticker related to the asset. Pass a comma-separated list for multiple tickers (for example aapl,spy). |

**Examples**

**Response:**

```
[
    {
        "ticker":"AAPL",
        "timestamp":"2019-01-30T22:33:38.186520297-05:00",
        "quoteTimestamp":"2019-01-30T22:33:38.186520297-05:00",
        "lastSaleTimestamp":"2019-01-30T22:33:34.176037579-05:00",
        "last":162.37,
        "lastSize":100,
        "tngoLast":162.33,
        "prevClose":154.68,
        "prevDate":"2019-01-29",
        "open":161.83,
        "high":163.25,
        "low":160.38,
        "mid":162.67,
        "volume":12500,
        "bidSize":100,
        "bidPrice":162.34,
        "askSize":100,
        "askPrice":163.0
    },
    {
        "ticker":"SPY",
        "timestamp":"2019-01-30T23:12:29.505261845-05:00",
        "quoteTimestamp":"2019-01-30T23:12:29.505261845-05:00",
        "lastSaleTimestamp":"2019-01-30T23:12:16.643833612-05:00",
        "last":265.41,
        "lastSize":617,
        "tngoLast":265.405,
        "prevClose":263.41,
        "prevDate":"2019-01-29",
        "open":264.62,
        "high":265.445,
        "low":264.225,
        "mid":265.405,
        "volume":8400,
        "bidSize":500,
        "bidPrice":265.39,
        "askSize":100,
        "askPrice":265.42
    }
]
    
```

### 2.7.3 Historical Overnight Intraday Prices Endpoint

Historical bars cover the BOATS overnight session only (**(8:00 PM–3:59 AM Eastern)**. When `startDate` and `endDate` are omitted, Tiingo returns the latest overnight session window.

**To request historical overnight intraday prices for a stock, use the following REST endpoint.**

```
# Historical Overnight Intraday Prices
https://api.tiingo.com/boats/<ticker>/prices?startDate=2026-07-22&resampleFreq=5min
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The date this data pertains to. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |
| `volume` | Volume | int64 | The number of shares traded on BOATS only. This value will only be exposed if explicitly passed to the "columns" request parameter. E.g. ?columns=open,high,low,close,volume |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset. |
| `startDate` | Start Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or after the startDate (>=). Parameter must be in YYYY-MM-DD format. When both dates are omitted, Tiingo returns the latest overnight BOATS session window (8:00 PM–3:59 AM ET). |
| `endDate` | End Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or before the endDate (<=). Parameter must be in YYYY-MM-DD format. Date-only endDate values are normalized to the BOATS overnight session end (3:59 AM ET). |
| `resampleFreq` | Resample Freq | no | string | This allows you to set the frequency in which you want data resampled. For example "1hour" would return the data where OHLC is calculated on an hourly schedule. The minimum value is "1min". Both units in minutes (min) and hours (hour) are accepted. Format is # + (min/hour); e.g. "15min" or "4hour". If no value is provided, defaults to 5min. |
| `afterHours` | After Hours | no | boolean | Accepted for request compatibility with other equity intraday endpoints, but it does not widen the BOATS historical window. BOATS history is limited to the overnight session (8:00 PM–3:59 AM ET) regardless of this flag. |
| `forceFill` | Force Fill | no | boolean | Some tickers do not have a trade/quote update for a given time period. if forceFill is set to true, then the previous OHLC will be used to fill the current OHLC. |

**Examples**

**Response:**

```
[
    {
        "date":"2026-07-22T01:00:00.000Z",
        "open":154.74,
        "high":155.52,
        "low":154.58,
        "close":154.76,
        "volume":16102
    },
    {
        "date":"2026-07-22T01:05:00.000Z",
        "open":154.8,
        "high":155.0,
        "low":154.31,
        "close":154.645,
        "volume":19127
    }
    ...
]
```
