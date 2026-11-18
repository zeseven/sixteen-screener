# Real-time & Historical Forex API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-forex`).

## Forex API Documentation - Real-time & Historical

**REST Endpoints**

The Forex API Endpoints are currently in beta.

```
# To request top-of-book/last for specific base and quote pairs, use the following REST endpoint
https://api.tiingo.com/tiingo/fx/<ticker>/top

# Historical Intraday Prices for base and quote pairs
https://api.tiingo.com/tiingo/fx/<ticker>/prices?startDate=2019-06-30&resampleFreq=5min
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.4.1 Overview of the Tiingo Forex API

Tiingo connects directly to tier-1 banks and FX dark pools to provide institutional-grade quality Forex quotes.

Benefits of the Tiingo Forex API

- 140+ Forex Tickers Quoted
- Data includes Top-of-Book (Bid/Ask) data
- Data now includes intraday OHLC bar historical data for all your needs.
- Quotes updated to the latest microsecond.
- Data is served via a REST API & Websocket API.
- Market hours are from 8pm EST Sunday through 5pm EST Friday.

For more details please visit the Forex API product page.

### 2.4.2 Current Top-of-Book

**To request top-of-book and last price data for a forex pair, use the following REST endpoints.**

```
# To request top-of-book/last for mulitple base and quote pairs, use the following REST endpoint
https://api.tiingo.com/tiingo/fx/top?tickers=<ticker>,<ticker>,...<ticker>

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/fx/<ticker>/top
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `quoteTimestamp` | Timestamp | datetime | The timestamp the data was last refresh on. |
| `midPrice` | Mid Price | float | The mid price of the current timestamp when both "bidPrice" and "askPrice" are not-null. In mathematical terms: midPrice = (bidPrice + askPrice)/2.0 |
| `bidSize` | Bid Size | float | The amount of units at the bid price. |
| `bidPrice` | Bid Price | float | The current bid price. |
| `askSize` | Ask Size | float | The amount of units at the ask price. |
| `askPrice` | Ask Price | float | The current ask price. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `tickers` | Ticker | no | string | Ticker related to the asset. |

**Examples**

**Response:**

```
[
    {
        "ticker":"audusd",
        "quoteTimestamp":"2019-07-01T21:00:01.289000+00:00",
        "bidPrice":0.6963,
        "bidSize":100000.0,
        "askPrice":0.69645,
        "askSize":1200000.0,
        "midPrice":0.696375
    },
    {
        "ticker":"eurusd",
        "quoteTimestamp":"2019-07-01T21:00:01.181000+00:00",
        "bidPrice":1.12849,
        "bidSize":250000.0,
        "askPrice":1.12864,
        "askSize":250000.0,
        "midPrice":1.128565
    }
]
    
```

### 2.4.3 Intraday Prices Endpoint

**To request historical intraday prices for a forex pair, use the following REST endpoint.**

```
# Current OHLC for the day
https://api.tiingo.com/tiingo/fx/<ticker>/prices?resampleFreq=1day

# Historical Intraday Prices
https://api.tiingo.com/tiingo/fx/<ticker>/prices?startDate=2019-06-30&resampleFreq=5min
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The date this data pertains to. |
| `ticker` | Ticker | string | Ticker related to the asset. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset. |
| `startDate` | Start Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or after the startDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or before the endDate (<=). Parameter must be in YYYY-MM-DD format. |
| `resampleFreq` | Resample Freq | no | string | his allows you to set the frequency in which you want data resampled. For example "1hour" would return the data where OHLC is calculated on an hourly schedule. The minimum value is "1min". Both units in minutes (min) and hours (hour) are accepted. Format is # + (min/hour); e.g. "15min" or "4hour". If no value is provided, defaults to 5min. |

**Examples**

**Response:**

```
[
    {
        "date":"2019-07-01T05:30:00.000Z",
        "ticker":"audusd",
        "open":0.699155,
        "high":0.69938,
        "low":0.699005,
        "close":0.69933
    },
    {
        "date":"2019-07-01T05:35:00.000Z",
        "ticker":"audusd",
        "open":0.69933,
        "high":0.69947,
        "low":0.699245,
        "close":0.699395
    }
    ...
]
```
