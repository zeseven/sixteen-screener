# Stock, ETF, and Mutual Fund Split API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-splits`).

## Stock, ETF, and Mutual Funds Splits API Documentation

**REST Endpoints**

The new Corporate Action endpoints, such as the splits endpoints below, are currently available to Beta-enabled customers and enterprise customers as an early release product. If you would like access to the data below, please E-mail support@tiingo.com

```
# Split data batch endpoint
https://api.tiingo.com/tiingo/corporate-actions/splits

# Split data endpoint for specific ticker
https://api.tiingo.com/tiingo/corporate-actions/<ticker>/splits
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.11.1 Overview

The Tiingo API Splits API endpoint provides detailed split data, for both historical and future split data. This endpoint remains newly exposed in beta and is included for all customers with the End-of-Day endpoint entitlement.

Splits are available for current and future dates, and data is updated as new corporate communications get processed. The tickers covered are the same found via the End-of-Day price data endpoints. This mean you can get past and future split data for stocks, ETFs, and mutual funds.

You can find out about the full product offering on the Product - Stock, ETF, & Mutual Fund Split API page.

### 2.11.2 Batch Split Data

Use this endpoint to get past, present, and future splits. This endpoint will return detailed split data for a given stock, ETF, or Mutual Fund. You will also notice split status - this can either be active ("a") or cancelled ("c").

**To request distribution data for a stock, use the following REST endpoints.**

```
# Latest Split Data with an Ex-Date of the current day
https://api.tiingo.com/tiingo/corporate-actions/splits

# Latest Split Data with an ex-date explicitly specified (future date or historical date)
https://api.tiingo.com/tiingo/corporate-actions/splits?exDate=2023-08-25
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `permaTicker` | PermaTicker | string | The Tiingo permaticker. |
| `ticker` | Ticker | string | Ticker related to the asset. |
| `exDate` | Ex-Date | datetime | The ex-Date of the split. In the Tiingo EOD Endpoints, this is the date where "splitFactor" will not be 1.0. This is also the date used for split adjustments. |
| `splitFrom` | Split From | float | The prior split ratio. |
| `splitTo` | Split To | float | The new split ratio, i.e. how many shares of "splitTo" are given for each share of "splitFrom". |
| `splitFactor` | Split Factor | float | The ratio of splitTo from splitFrom. In other words: splitFactor = splitTo/splitFrom This ratio is helpful in calculating split price adjustments. |
| `splitStatus` | Split Status | string | A code representing the status of split ; a: Active ; c: Cancelled |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `exDate` | Ex-Date | no | date | This filter limits distributions that have an ex-date on the date passed. Parameter must be in YYYY-MM-DD format. |

**Examples**

**Response:**

```
[
    {
        "permaTicker":"US000000060663",
        "ticker":"dinrf",
        "exDate":"2023-09-28T00:00:00.000Z",
        "splitFrom":1.0,
        "splitTo":2.0,
        "splitFactor":2.0
    }
    ...
]
    
```

### 2.11.3 Ticker-specific Split Data

This endpoint is similar to the batch endpoint, but allows you to specify a specific ticker to limit the query and also provide historical split timeseries data for a ticker as well. Stocks, ETFs, and Mutual Funds are supported.

**To request distribution data for a stock, use the following REST endpoints.**

```
# Latest Split Data with an Ex-Date of the current day
https://api.tiingo.com/tiingo/corporate-actions/cvs/splits

# Latest Split Data with an ex-date explicitly specified (future date or historical date)
https://api.tiingo.com/tiingo/corporate-actions/cvs/splits?startExDate=2002-08-25
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `permaTicker` | PermaTicker | string | The Tiingo permaticker. |
| `ticker` | Ticker | string | Ticker related to the asset. |
| `exDate` | Ex-Date | datetime | The ex-Date of the split. In the Tiingo EOD Endpoints, this is the date where "splitFactor" will not be 1.0. This is also the date used for split adjustments. |
| `splitFrom` | Split From | float | The prior split ratio. |
| `splitTo` | Split To | float | The new split ratio, i.e. how many shares of "splitTo" are given for each share of "splitFrom". |
| `splitFactor` | Split Factor | float | The ratio of splitTo from splitFrom. In other words: splitFactor = splitTo/splitFrom This ratio is helpful in calculating split price adjustments. |
| `splitStatus` | Split Status | string | A code representing the status of split ; a: Active ; c: Cancelled |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset you would like distribution data for. |
| `startExDate` | Start Ex-Date | no | date | This filter limits distributions that have an ex-date >= on the date passed. Parameter must be in YYYY-MM-DD format. |
| `endExDate` | End Ex-Date | no | date | This filter limits distributions that have an ex-date <= on the date passed. Parameter must be in YYYY-MM-DD format. |

**Examples**

**Response:**

```
[
    {
        "permaTicker":"US000000102867",
        "ticker":"dcfc",
        "exDate":"2024-04-02T04:00:00.000Z",
        "splitFrom":200.0,
        "splitTo":1.0,
        "splitFactor":0.005
    }
    ...
]
    
```
