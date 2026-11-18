# Real-time & Historical IEX API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-iex`).

**REST Endpoints**

We recommend switching to 24x5

Our new 24x5 Consolidated Real-time feed is the successor to IEX Derived - one consolidated price and liquidity spread per stock from multiple venues, Sunday 8:00 PM ET through Friday 8:00 PM ET, included on every plan. IEX Derived is still included, so existing integrations keep working. Start with the 24x5 REST docs.

```
# To request top-of-book/last for all tickers, use the following REST endpoint
https://api.tiingo.com/iex

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/iex/<ticker>

# Historical Intraday Prices
https://api.tiingo.com/iex/<ticker>/prices?startDate=2019-01-02&resampleFreq=5min
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.6.1 Overview

Our IEX Exchange API has two kinds of avenues for (1) Customers who are officially registered with the exchange and (2) Customers who want a simple, compliant solution, but do not want to formally register with the exchange.

1. Tiingo has a cross-correct directly to the IEX Exchange that gives us access to raw binary price feeds we can share with you all. We receive IEX TOPS (top of book), which means we get the last sale data AND top bid and ask quotes.

2. For customers that don't want to register with the exchange to receive full TOPS, you may use our derived reference price feed which provides a real-time reference price for the asset - giving you the benefit of a real-time feed without the added compliance or exchange fees required.

Benefits of Tiingo + IEX

- 14,000+ tickers IEX provides quotes and trade data on.
- Data includes Top-of-Book (Bid/Ask) and Last Sale (trade) Data.
- Data now includes intraday OHLC bar historical data for all your needs.
- Tiingo enriches the data, giving you more data for your convenience.
- Quotes updated to the latest nanosecond.
- Access to every field IEX gives us access to (including trade flags).
- Data is served via a REST API & Websocket API.

**As of February 1st, 2025** IEX Exchange has changed their market data policies. To receive the FULL TOPS Feed, you must now have a market data agreement signed with the IEX Exchange. Upon signing, you will then be able to receive the full TOPS feed in real-time.

For customers who do not want to sign a license agreement, you may use our derived data that calculates a reference price for each asset in real-time. While this is not a subsitute for the TOPS Feed, we do believe it will fulfill the needs of 95% of our customer base. There is no additional cost to the IEX Exchange if using our derived data.

You can find out about the full product offering on the Product - IEX page.

### 2.6.2 Current Top-of-Book & Last Price

**To request top-of-book and last price data for a stock, use the following REST endpoints.**

```
# To request top-of-book/last for all tickers, use the following REST endpoint
https://api.tiingo.com/iex

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/iex/<ticker>
```

**Response**

"IEX Entitlement Required" means the value will be null unless you are registered with the IEX Exchange and have a market data policy in place.

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `timestamp` | Timestamp | datetime | The timestamp the data was last refresh on. |
| `quoteTimestamp` | Quote Timestamp | datetime | The timestamp the last time the quote (bid/ask) data was received from IEX. IEX entitlement required |
| `lastSaleTimestamp` | Last Sale Timestamp | datetime | The timestamp the last time the trade (last/lastSize) data was received from IEX. IEX entitlement required |
| `last` | last | float | Last is the last trade that was executed on IEX. IEX entitlement required |
| `lastSize` | Last Size | int32 | The amount of shares traded (volume) at the last price on IEX. IEX entitlement required |
| `tngoLast` | Tiingo Last | float | Tiingo Last is either the last price or mid price. The mid price is only used if our algo determines it is a good proxy for the last price. So if the spread is considered wide by our algo, we do not use it. Also, after the official exchange print comes in, this value changes to that value. This value is calculated by Tiingo and not provided by IEX. |
| `prevClose` | Previous Close | float | Previous day's closing price of the security.This can be from any of the exchanges, NYSE, NASDAQ, IEX, etc. |
| `open` | Open | float | The opening price of the asset on the current day This value is calculated by Tiingo and not provided by IEX. |
| `high` | High | float | The high price of the asset on the current day This value is calculated by Tiingo and not provided by IEX. |
| `low` | Low | float | The low price of the asset on the current day This value is calculated by Tiingo and not provided by IEX. |
| `mid` | Mid | float | The mid price of the current timestamp when both "bidPrice" and "askPrice" are not-null. In mathematical terms: mid = (bidPrice + askPrice)/2.0 This value is calculated by Tiingo and not provided by IEX. |
| `volume` | Volume | int64 | Volume will be IEX Volume throughout the day, but once the official closing price comes in, volume will reflect the volume done on the entire day across all exchanges. This field is available for convenience. |
| `bidSize` | Bid Size | float | The amount of shares at the bid price. IEX entitlement required |
| `bidPrice` | Bid Price | float | The current bid price. IEX entitlement required |
| `askSize` | Ask Size | float | The amount of shares at the ask price. IEX entitlement required |
| `askPrice` | Ask Price | float | The current ask price. IEX entitlement required |

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
        "quoteTimestamp":"2019-01-30T10:33:38.186520297-05:00"
        "lastSaleTimeStamp":"2019-01-30T10:33:34.176037579-05:00",
        "last":162.37,
        "lastSize":100,
        "tngoLast":162.33,
        "prevClose":154.68,
        "open":161.83,
        "high":163.25,
        "low":160.38,
        "mid":162.67,
        "volume":0,
        "bidSize":100,
        "bidPrice":162.34,
        "askSize":100,
        "askPrice":163.0
    },
    {
        "ticker":"SPY",
        "timestamp":"2019-01-30T11:12:29.505261845-05:00",
        "quoteTimestamp":"2019-01-30T11:12:29.505261845-05:00"
        "lastSaleTimeStamp":"2019-01-30T11:12:16.643833612-05:00",
        "last":265.41,
        "lastSize":617,
        "tngoLast":265.405,
        "prevClose":263.41,
        "open":264.62,
        "high":265.445,
        "low":264.225,
        "mid":265.405,
        "volume":0,
        "bidSize":500,
        "bidPrice":265.39,
        "askSize":100,
        "askPrice":265.42
    }
]
    
```

### 2.6.3 Historical Intraday Prices Endpoint

**To request historical intraday prices for a stock, use the following REST endpoint.**

```
# Historical Intraday Prices
https://api.tiingo.com/iex/<ticker>/prices?startDate=2019-01-02&resampleFreq=5min
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The date this data pertains to. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |
| `volume` | Volume | int64 | The number of shares traded on IEX only. This value will only be exposed if explicitly passed to the "columns" request parameter. E.g. ?columns=open,high,low,close,volume |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset. |
| `startDate` | Start Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or after the startDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or before the endDate (<=). Parameter must be in YYYY-MM-DD format. |
| `resampleFreq` | Resample Freq | no | string | his allows you to set the frequency in which you want data resampled. For example "1hour" would return the data where OHLC is calculated on an hourly schedule. The minimum value is "1min". Both units in minutes (min) and hours (hour) are accepted. Format is # + (min/hour); e.g. "15min" or "4hour". If no value is provided, defaults to 5min. |
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
