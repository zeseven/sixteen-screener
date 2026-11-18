# 24x5 Consolidated Real-time Stock API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-equity-realtime-stock-data`).

**REST Endpoints**

Recommended for new integrations

The 24x5 Consolidated Real-time endpoints cover Sunday 8:00 PM ET through Friday 8:00 PM ET and are included on every plan. The IEX endpoints remain supported for existing integrations. See the product overview to compare with IEX and BOATS.

```
# To request reference price and liquidity metrics for all tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/equity/intraday

# To request reference price and liquidity metrics for specific tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/equity/intraday/<ticker>

# Historical Intraday Prices
https://api.tiingo.com/tiingo/equity/intraday/<ticker>/prices?startDate=2019-01-02&resampleFreq=5min
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.5.1 Overview

The 24x5 Consolidated Real-time endpoints are Tiingo's recommended real-time US equity feed for new integrations. They create derived datasets from multiple equity venues (exchanges, ATS, and OTC venues) from Sunday 8:00 PM ET through Friday 8:00 PM ET. Tiingo is the first company to make these exchange-compliant derived metrics and offer them for free.

The IEX endpoints remain supported and included on every plan, so you can use either feed or both. For a comparison with IEX, BOATS, and SIP data, see the 24x5 Consolidated Real-time product page.

This changes the game on exchange liquidity and the products we can offer to provide better reference pricing for risk and valuation metrics for your portfolio valuations, risk assessments, trading, and app/AI development.

The data is sourced from Tiingo's consolidated equity pipeline and exposed through `/tiingo/equity/intraday`. Historical bars use consolidated intraday data where available and preserve the familiar start date, end date, resampling, after-hours, and force-fill controls.

Benefits of Tiingo 24x5 Consolidated Real-time

- Realtime consolidated equity snapshots for supported US equities and ETFs.
- 24x5 coverage from Sunday 8:00 PM ET through Friday 8:00 PM ET: 4:00 AM to 8:00 PM ET daytime and extended hours, plus 8:00 PM to 3:59 AM ET overnight coverage Sunday through Thursday.
- Tiingo reference price, liquidity reference price, liquidity spread and bid/ask metrics, and day-level OHLC fields.
- Historical intraday OHLCV bars with configurable minute and hourly resampling.
- Optional after-hours data and force-filled bars for charting workflows.
- Data is served via REST endpoints and available via WebSockets as well.

### 2.5.2 Current Reference Price & Liquidity Snapshot

**To request reference price and liquidity metrics for a stock, use the following REST endpoints.**

```
# To request reference price and liquidity metrics for all tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/equity/intraday

# To request reference price and liquidity metrics for specific tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/equity/intraday/<ticker>
```

**Response**

Fields may be null when the underlying consolidated feed has not published that value.

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `timestamp` | Timestamp | datetime | The timestamp the data was last refresh on. |
| `tngoLast` | Tiingo Last | float | Tiingo Last is either the last price or mid price. The mid price is only used if our algo determines it is a good proxy for the last price. So if the spread is considered wide by our algo, we do not use it. Also, after the official exchange print comes in, this value changes to that value. This value is calculated by Tiingo from the consolidated equity feed. |
| `lqRefPrice` | Liquidity Reference Price | float | The same as tngoLast - mirrored for convenience/consistency as it's better-named for its role in the lq spread metrics. |
| `prevClose` | Previous Close | float | The official closing price (closing auction) taken from Tiingo's end-of-day prices (adjClose), so it is adjusted for splits and dividends. prevDate gives the trading date it belongs to. Before 8:00pm ET this is the previous trading day's close; at 8:00pm ET, when the overnight session starts, it switches to the current day's close. If the official close is not available yet at 8:00pm ET, this is temporarily the last price traded before 8:00pm ET and prevDate is null. |
| `prevDate` | Previous Close Date | date | The trading date (YYYY-MM-DD) of the official close reported in prevClose. Null when prevClose is a temporary last traded price because the official close is not available yet. |
| `open` | Open | float | The opening price of the asset on the current day This value is calculated by Tiingo from the consolidated equity feed. |
| `high` | High | float | The high price of the asset on the current day This value is calculated by Tiingo from the consolidated equity feed. |
| `low` | Low | float | The low price of the asset on the current day This value is calculated by Tiingo from the consolidated equity feed. |
| `volume` | Volume | int64 | Volume will be consolidated intraday volume throughout the day. Once the official closing price comes in, volume may reflect the full official trading day. This field is available for convenience. |
| `lqSpread` | Liquidity Spread | float | The relative lqBid/Ask spread component of the liquidity risk metric, expressed as a decimal (e.g. 0.04 means 4%). Corresponds to lqSpread in the thresholdLevel 4 websocket liquidity risk metric. |
| `lqBidPrice` | Liquidity Bid Price | float | The bid price component of the liquidity risk metric. Corresponds to lqBidPrice in the thresholdLevel 4 websocket liquidity risk metric. |
| `lqBidSize` | Liquidity Bid Size | int64 | The bid size component of the liquidity risk metric in shares. Corresponds to lqBidSize in the thresholdLevel 4 websocket liquidity risk metric. |
| `lqAskPrice` | Liquidity Ask Price | float | The ask price component of the liquidity risk metric. Corresponds to lqAskPrice in the thresholdLevel 4 websocket liquidity risk metric. |
| `lqAskSize` | Liquidity Ask Size | int64 | The ask size component of the liquidity risk metric in shares. Corresponds to lqAskSize in the thresholdLevel 4 websocket liquidity risk metric. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `tickers` | Ticker | no | string | Ticker related to the asset. |

**Examples**

**Response:**

```
[
    {
        "ticker":"AAPL",
        "timestamp":"2019-01-30T10:33:38.186520297-05:00",
        "tngoLast":162.33,
        "lqRefPrice":162.33,
        "prevClose":154.68,
        "prevDate":"2019-01-29",
        "open":161.83,
        "high":163.25,
        "low":160.38,
        "volume":0,
        "lqSpread":0.0011,
        "lqBidPrice":162.30,
        "lqBidSize":100,
        "lqAskPrice":162.36,
        "lqAskSize":100
    },
    {
        "ticker":"SPY",
        "timestamp":"2019-01-30T11:12:29.505261845-05:00",
        "tngoLast":265.405,
        "lqRefPrice":265.405,
        "prevClose":263.41,
        "prevDate":"2019-01-29",
        "open":264.62,
        "high":265.445,
        "low":264.225,
        "volume":0,
        "lqSpread":0.0008,
        "lqBidPrice":265.39,
        "lqBidSize":500,
        "lqAskPrice":265.42,
        "lqAskSize":100
    }
]
    
```

### 2.5.3 Historical Intraday Prices Endpoint

**To request historical intraday prices for a stock, use the following REST endpoint.**

```
# Historical Intraday Prices
https://api.tiingo.com/tiingo/equity/intraday/<ticker>/prices?startDate=2019-01-02&resampleFreq=5min
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The date this data pertains to. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |
| `volume` | Volume | int64 | The consolidated number of shares traded for the interval. This value will only be exposed if explicitly passed to the "columns" request parameter. E.g. ?columns=open,high,low,close,volume |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset. |
| `startDate` | Start Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or after the startDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or before the endDate (<=). Parameter must be in YYYY-MM-DD format. |
| `resampleFreq` | Resample Freq | no | string | This allows you to set the frequency in which you want data resampled. For example "1hour" would return the data where OHLC is calculated on an hourly schedule. The minimum value is "1min". Both units in minutes (min) and hours (hour) are accepted. Format is # + (min/hour); e.g. "15min" or "4hour". If no value is provided, defaults to 5min. |
| `afterHours` | After Hours | no | boolean | If set to true, includes pre and post market data if available. |
| `forceFill` | Force Fill | no | boolean | Some tickers do not have a trade/quote update for a given time period. if forceFill is set to true, then the previous OHLC will be used to fill the current OHLC. |

**Examples**

**Response:**

```
[
    {
        "date":"2019-01-02T14:30:00.000Z",
        "open":154.74,
        "high":155.52,
        "low":154.58,
        "close":154.76,
        "volume":16102
    },
    {
        "date":"2019-01-02T14:35:00.000Z",
        "open":154.8,
        "high":155.0,
        "low":154.31,
        "close":154.645,
        "volume":19127
    }
    ...
]
```
