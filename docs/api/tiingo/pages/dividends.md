# Stock, ETF, and Mutual Fund Dividend API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-dividends`).

## Stock, ETF, and Mutual Funds Dividend API Documentation

**REST Endpoints**

The new Corporate Action endpoints, such as the distribution endpoints below, are currently available to Beta-enabled customers and enterprise customers as an early release product. If you would like access to the data below, please E-mail support@tiingo.com

```
# Distribution data batch endpoint
https://api.tiingo.com/tiingo/corporate-actions/distributions

# Distribution data endpoint for specific ticker
https://api.tiingo.com/tiingo/corporate-actions/<ticker>/distributions

# Historical timeseries of distribution (dividend) yield
https://api.tiingo.com/tiingo/corporate-actions/<ticker>/distribution-yield
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.10.1 Overview

The Tiingo API Dividends API endpoint help capture and relay both detailed dividend and distribution data, as well as historical metrics that relate to dividends, such as yield. As of August 28th, 2023, this endpoint remains newly exposed in beta and is included for all customers with the End-of-Day endpoint entitlement. This dividend distribution API endpoints fall under our new corporate action endpoints coming soon.

Diviends are available for current and future dates, and yield is available for tickers after their End-of-Day price data have been processed.

You can find out about the full product offering on the Product - Stock, ETF, & Mutual Fund Dividend API page.

### 2.10.2 Batch Distribution Data

Use this endpoint to get past, present, and future dividends and distributions. This endpoint will return detailed dividend and distribution data for a given stock, ETF, or Mutual Fund. You will also notice a distribution frequency, this is the declared frequency of the distribution that you can use to customize your calculations to determine yield.

**To request distribution data for a stock, use the following REST endpoints.**

```
# Latest Distribution Data with an Ex-Date of the current day
https://api.tiingo.com/tiingo/corporate-actions/distributions

# Latest Distribution Data with an ex-date explicitly specified (future date or historical date)
https://api.tiingo.com/tiingo/corporate-actions/distributions?exDate=2023-08-25
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `permaTicker` | PermaTicker | string | The Tiingo permaticker. |
| `ticker` | Ticker | string | Ticker related to the asset. |
| `exDate` | Ex-Date | datetime | The ex-Date of the distribution. In the Tiingo EOD Endpoints, this is the date where "divCash" will be non-zero. This is also the date used for dividend price adjustments. |
| `paymentDate` | Payment Date | datetime | The payment date of the distribution. |
| `recordDate` | Record Date | datetime | The record date of the distribution. |
| `declarationDate` | Declaration Date | datetime | The declaration date of the distribution. |
| `distribution` | Distribution | float | The total distribution for the given date. |
| `distributionFrequency` | Distribution Frequency | string | The frequency that's associated with this distribution. For example "q" means quarterly, meaning this is a declared quarterly distribution. The full list of codes is available here: ; w: Weekly ; bm: Bimonthly ; m: Monthly ; tm: Trimesterly ; q: Quarterly ; sa: Semiannually ; a: Annually ; ir: Irregular ; f: Final ; u: Unspecified ; c: Cancelled |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `exDate` | Ex-Date | no | date | This filter limits distributions that have an ex-date on the date passed. Parameter must be in YYYY-MM-DD format. |

**Examples**

**Response:**

```
[
    {
        "permaTicker":"US000000000045",
        "ticker":"ge",
        "exDate":"2023-09-25T00:00:00.000Z",
        "paymentDate":"2023-10-25T00:00:00.000Z",
        "recordDate":"2023-09-26T00:00:00.000Z",
        "declarationDate":"2023-09-08T04:00:00.000Z",
        "distribution":0.08,
        "distributionFrequency":"q"
    },
    ...
]
    
```

### 2.10.3 Ticker-specific Distribution Data

This endpoint is similar to the batch endpoint, but allows you to specify a specific ticker to limit the query and also provide historical distribution timeseries data for a ticker as well. Stocks, ETFs, and Mutual Funds are supported.

**To request distribution data for a stock, use the following REST endpoints.**

```
# Distribution data endpoint for specific ticker (Full history)
https://api.tiingo.com/tiingo/corporate-actions/<ticker>/distributions

# Distribution data for a ticker limited to a provided date range
https://api.tiingo.com/tiingo/corporate-actions/<ticker>/distributions?startExDate=2023-01-01&endExDate=2024-01-01
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `permaTicker` | PermaTicker | string | The Tiingo permaticker. |
| `ticker` | Ticker | string | Ticker related to the asset. |
| `exDate` | Ex-Date | datetime | The ex-Date of the distribution. In the Tiingo EOD Endpoints, this is the date where "divCash" will be non-zero. This is also the date used for dividend price adjustments. |
| `paymentDate` | Payment Date | datetime | The payment date of the distribution. |
| `recordDate` | Record Date | datetime | The record date of the distribution. |
| `declarationDate` | Declaration Date | datetime | The declaration date of the distribution. |
| `distribution` | Distribution | float | The total distribution for the given date. |
| `distributionFrequency` | Distribution Frequency | string | The frequency that's associated with this distribution. For example "q" means quarterly, meaning this is a declared quarterly distribution. The full list of codes is available here: ; w: Weekly ; bm: Bimonthly ; m: Monthly ; tm: Trimesterly ; q: Quarterly ; sa: Semiannually ; a: Annually ; ir: Irregular ; f: Final ; u: Unspecified ; c: Cancelled |

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
        "permaTicker":"US000000000038",
        "ticker":"aapl",
        "exDate":"2024-02-09T05:00:00.000Z",
        "paymentDate":"2024-02-15T05:00:00.000Z",
        "recordDate":"2024-02-12T05:00:00.000Z",
        "declarationDate":"2024-02-01T10:00:00.000Z",
        "distribution":0.24,
        "distributionFrequency":"q"
    },
    ...
]
    
```

### 2.10.4 Historical Yield Endpoint

Use this endpoint to obtain the current and historical information about yield data for the given Stock, ETF, or Mutual Fund. Please note that we will continue to add new daily metrics, so the fields will change throughout time. We recommend you **do not** make parsing code that requires columns or fields to be in a particular order. If you do require this, please use the **columns** request parameter to ensure constant output, even if we add columns. For example, **columns=trailingDiv1Y** will always ensure only that single field is returned in that exact order.

**To request historical distribution yield data for a stock, use the following REST endpoint.**

```
# Distribution yield
https://api.tiingo.com/tiingo/corporate-actions/<ticker>/distribution-yield
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | Date associated with the yield. |
| `trailingDiv1Y` | Trailing 1 Year Dividend | string | The trailing distribution yield for the asset based on the previous 1 year of distributions. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Ticker | yes | string | Ticker related to the asset you would like yield data for. |

**Examples**

**Response:**

```
[
...
    {
        "date":"2023-08-18T00:00:00.000Z",
        "trailingDiv1Y":0.0053871282
    },
    {
        "date":"2023-08-21T00:00:00.000Z",
        "trailingDiv1Y":0.0053457689
    },
    {
        "date":"2023-08-22T00:00:00.000Z",
        "trailingDiv1Y":0.0053038425
    },
    {
        "date":"2023-08-23T00:00:00.000Z",
        "trailingDiv1Y":0.0051899293
    }
]
```

### 2.10.5 Additional Information & FAQ

This endpoint is used to communicate past, present, and future distribtion data.

##### How often does this data update?

Data is updated throughout 2-3 times a day as distribution announcements come in.

##### Will more types of yield be added?

Yes, future expansions of various yield calculations will be included in future dates - including forward yield. You may also calculate this yourself using our distribution frequency endpoints.

##### How come some dates are null?

We have different enterprise relationships with various provides who provide various data feeds. We then error check them using a proprietary framework to check for errors. If a date is null, it generally means this data is not available to us. Ex-date and distribution amounts will never be null however.
