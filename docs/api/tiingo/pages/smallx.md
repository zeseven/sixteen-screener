# Real-time & Historical Small Exchange API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-smallx`).

**REST Endpoints**

```
# To request a list of contracts & meta data for all tickers, use the following REST endpoint
https://api.tiingo.com/smallx/meta

# To request top-of-book/last for all tickers, use the following REST endpoint
https://api.tiingo.com/smallx/tops

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/smallx/<ticker>/tops

# Historical Intraday Prices
https://api.tiingo.com/smallx/<ticker>/prices?startDate=2022-08-02&resampleFreq=5min

# Historical Official End-of-Day Prices
https://api.tiingo.com/smallx/<ticker>/eod?startDate=2022-08-02
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.6.1 Overview

Tiingo has a cross-correct directly to the Small Exchange Exchange that gives us access to raw binary price feeds we can share with you all.

Benefits of Tiingo + Small Exchange

- For the first time, you now have Real-time 2Y, 10Y, and 30Y interest rate futures, Oil, US Dollar, equity index futures, and so much more.
- Small-Exchange has favorable retail products that are innovating both product scope as well as improving accessibility of retail to futures data.
- Small Exchange has a $0 (zero) USD license fee policy
- Data includes Top-of-Book (Bid/Ask) and Last Sale (trade) Data.
- Data includes intraday OHLC bar historical data for all your needs.
- Tiingo enriches the data, giving you more data for your convenience.
- Quotes updated to the latest nanosecond.
- Access to almost every field SmallX gives us access to.
- Data is served via a REST API & Websocket API.

You can find out about the full product offering on the Product - Small Exchange page.

### 2.6.2 Meta Data

You can use this meta endpoint to keep updated with all of the available contracts from the Small Exchange, and use it to keep your database up to date as new contracts come in and old ones expire. This endpoint gives the master list of tickers, and associated meta data, that you can use to query data across our Small Exchange APIs.

**To request historical intraday prices for a stock, use the following REST endpoint.**

```
# Historical End-of-Day Prices
https://api.tiingo.com/smallx/meta
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `permaTicker` | Perma Ticker | string | Permanent Tiingo Ticker mapping to the security. Can be used as a primary key. |
| `ticker` | Ticker | string | Ticker related to the asset. |
| `name` | Name | string | Full-length name of the asset. |
| `baseTicker` | Base Ticker | string | The base ticker for the given contract. This is the base tickers that the calendar and year identifier are appended to in order to create the Small Exchange ticker. E.g. "SMO" |
| `baseCurrency` | Base Currency | string | The currency the contract is denominated in. |
| `cfiCode` | CFI Code | string | The standardized CFI code for the given contract. |
| `firstTradingDate` | Fist Trading Date | date | The first date the contract was able to be traded. |
| `lastTradingDate` | Last Trading Date | date | The last date the contract is tradeable. |
| `expirationDate` | Expiration Date | date | The date the contract expires. |
| `maturityDate` | Maturity Date | date | The date the contract reaches maturity. |
| `tickSize` | Tick Size | float | The smallest amount the contract can increment or decrement by. |
| `tickMulitplier` | Tick Multiplier | float | The amount you need to multiple the contract price by to get the notional (in baseCurrency) of the value of the contract. E.g. a price of 35.0 with a baseCurrency of USD and multiplier of 100, means each contract is worth $3500.00 (35 * 100). |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `tickers` | Tickers | no | string | Specific tickers to return meta data for. If no string passed, will return meta data for all available tickers. Can either be a single ticker, a comma-separated list of tickers, or an array of strings (string[]). |

**Examples**

**Response:**

```
[
    {
        "ticker":"smesu22",
        "permaTicker":"FU000000000033",
        "baseTicker":"smes",
        "cfiCode":"FFICSX",
        "baseCurrency":"usd",
        "maturityDate":"2022-09-16",
        "firstTradingDate":"2022-07-18",
        "lastTradingDate":"2022-09-16",
        "expirationDate":"2022-09-16",
        "tickSize":0.01,
        "tickMultiplier":100.0
    }
    ...
]                                    
```

### 2.6.3 Current Top-of-Book & Last Price

The following endpoints curate the data as we receive it from the Small Exchange into a friendly-to-use format. The open, high, low, and last in these endpoints utilize a unique Tiingo algorithm that updates prices more frequently than last trade. The Open, High, Low, and Last prices are calculated by the "tngoLast" field which you can read more about in the table below.

**To request top-of-book and last price data for a stock, use the following REST endpoints.**

```
# To request top-of-book/last for all tickers, use the following REST endpoint
https://api.tiingo.com/smallx/tops

# To request top-of-book/last for specific tickers, use the following REST endpoint
https://api.tiingo.com/smallx/<ticker>/tops
or
https://api.tiingo.com/smallx/tops?tickers=<ticker>
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `timestamp` | Timestamp | datetime | The timestamp the data was last refresh on. |
| `quoteTimestamp` | Quote Timestamp | datetime | The timestamp the last time the quote (bid/ask) data was received from the Small Exchange. |
| `lastSaleTimestamp` | Last Sale Timestamp | datetime | The timestamp the last time the trade (last/lastSize) data was received from Small Exchange. |
| `last` | last | float | Last is the last trade that was executed on Small Exchange. |
| `lastSize` | Last Size | int32 | The amount of shares traded (volume) at the last price on Small Exchange. |
| `tngoLast` | Tiingo Last | float | Tiingo Last is either the last price or mid price. The mid price is only used if our algo determines it is a good proxy for the last price. So if the spread is considered wide by our algo, we do not use it. Also, after the official exchange print comes in, this value changes to that value. This value is calculated by Tiingo and not provided by the Small Exchange. |
| `prevClose` | Previous Close | float | Previous day's closing price of the security. |
| `open` | Open | float | The opening price of the asset on the current day as set by "tngoLast" in the docs. This value is calculated by Tiingo and not provided by the Small Exchange. |
| `high` | High | float | The high price of the asset on the current day as set by "tngoLast" in the docs. This value is calculated by Tiingo and not provided by the Small Exchange. |
| `low` | Low | float | The low price of the asset on the current day as set by "tngoLast" in the docs. This value is calculated by Tiingo and not provided by the Small Exchange. |
| `low` | Low | float | The close price of the asset on the current day as set by "tngoLast" in the docs. This value is calculated by Tiingo and not provided by the Small Exchange. |
| `mid` | Mid | float | The mid price of the current timestamp when both "bidPrice" and "askPrice" are not-null. In mathematical terms: mid = (bidPrice + askPrice)/2.0 This value is calculated by Tiingo and not provided by the Small Exchange. |
| `volume` | Volume | int64 | Total volume traded on the Small Exchange for the given instrument. |
| `bidSize` | Bid Size | float | The amount of shares at the bid price. |
| `bidPrice` | Bid Price | float | The current bid price. |
| `askSize` | Ask Size | float | The amount of shares at the ask price. |
| `askPrice` | Ask Price | float | The current ask price. |
| `openInterest` | Open interest | int64 | The current Open Interest for the given instrument. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `tickers` | Ticker | no | string | Ticker related to the asset. |

**Examples**

**Response:**

```
[
    {
        "ticker":"S10YQ22",
        "timestamp":"2022-08-12T12:39:03.355783+00:00",
        "lastSaleTimestamp":"2022-08-12T12:33:55.578814+00:00",
        "quoteTimestamp":"2022-08-12T12:39:03.355783+00:00",
        "open":28.69,
        "high":28.69,
        "low":28.42,
        "mid":28.52,
        "tngoLast":28.515,
        "last":28.45,
        "lastSize":1,
        "bidSize":24,
        "bidPrice":28.47,
        "askPrice":28.57,
        "askSize":24,
        "prevClose":28.81,
        "openInterest":1653,
        "volume":4
    },                                    
...
]
    
```

### 2.6.4 Historical Intraday Prices Endpoint

The historical Open, High, Low, and Close prices are calculating using a Tiingo Algorithm that results in fuller charts. The values are calculated using the "tngoLast" field described in the table above. This means the charts look fuller, and update more frequently, even if no trades happened. In our opinion, this better reflects the prices of the underlying instruments, more than using trades alone.

**To request historical intraday prices for a stock, use the following REST endpoint.**

```
# Historical Intraday Prices
https://api.tiingo.com/smallx/<ticker>/prices?startDate=2022-08-02&resampleFreq=5min
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The date this data pertains to. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |
| `volume` | Volume | int64 | The number of shares traded on the Small Exchange. |

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
        "date":"2022-08-02T16:40:00.000Z",
        "open":95.81,
        "high":95.905,
        "low":95.71,
        "close":95.8,
        "volume":3.0
    },
    {
        "date":"2022-08-02T16:45:00.000Z",
        "open":95.8,
        "high":95.91,
        "low":95.49,
        "close":95.545,
        "volume":2.0
    },
    {
        "date":"2022-08-02T16:50:00.000Z",
        "open":95.545,
        "high":95.88,
        "low":95.54,
        "close":95.835,
        "volume":2.0
    },
    ...
]
```

### 2.6.5 Official End-of-Day Data

The historical Open, High, Low, Close, Settlement, Volume, and Open Interest prices are calculated by the Small Exchange and disseminated throughout the day. For OHLCV, these values use the trades done on the exchange. This endpoint includes Open Interest and Settlement Price, which are official values by the Small Exchange and not available in the intraday endpoint.

**To request historical intraday prices for a stock, use the following REST endpoint.**

```
# Historical End-of-Day Prices
https://api.tiingo.com/smallx/<ticker>/eod?startDate=2022-08-02
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The date this data pertains to. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |
| `settlementPrice` | Settlement Price | float | The settlement price of the contract as determined by the Small Exchange. |
| `volume` | Volume | int64 | The number of shares traded on the Small Exchange. |
| `openInterest` | Open Interest | int64 | The open interest of the contract. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset. |
| `startDate` | Start Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or after the startDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or before the endDate (<=). Parameter must be in YYYY-MM-DD format. |
| `resampleFreq` | Resample Freq | no | string | Allows resampled values that allow you to choose the values returned as daily, weekly, monthly, or annually values. Note: ONLY DAILY takes into account holidays. All others use standard business days Acceptable values: ; daily: Values returned as daily periods, with a holiday calendar. ; weekly: Values returned as weekly data, with days ending on Friday. ; monthly: Values returned as monthly data, with days ending on the last standard business day (Mon-Fri) of each month. ; annually: Values returned as annual data, with days ending on the last standard business day (Mon-Fri) of each year. Note, that if you choose a value in-between the resample period for weekly, monthly, and daily, the start date rolls back to consider the entire period. For example, if you choose to resample weekly, but your "startDate" parameter is set to Wednesday of that week, the startDate will be adjusted to Monday, so the entire week is captured. Another example is if you send a startDate mid-month, we roll back the startDate to the beginning of the month. Similarly, if you provide an endDate, and it's midway through the period, we roll-forward the date to capture the whole period. In the above example, if the end date is set to a wednesday with a weekly resample, the end date is rolled forward to the Friday of that week. |

**Examples**

**Response:**

```
[
    {
        "date":"2022-08-02T21:00:00.000Z",
        "open":94.25,
        "high":96.26,
        "low":93.66,
        "close":94.31,
        "settlementPrice":94.31,
        "volume":57,
        "openInterest":66
    },
    {
        "date":"2022-08-03T21:00:00.000Z",
        "open":96.0,
        "high":96.25,
        "low":90.49,
        "close":90.99,
        "settlementPrice":90.99,
        "volume":63,
        "openInterest":43
    }
    ...
]
```
