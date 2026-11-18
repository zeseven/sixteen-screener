# Real-time & Historical CryptoCurrency (Crypto) Price API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-crypto`).

**REST Endpoints**

```
# Top-of-Book Data for specific tickers
https://api.tiingo.com/tiingo/crypto/top?tickers=btcusd,fldcbtc

# Real-time (Latest) Data for specific tickers
https://api.tiingo.com/tiingo/crypto/prices?tickers=btcusd,fldcbtc

# Historical Prices
https://api.tiingo.com/tiingo/crypto/prices?tickers=btcusd,fldcbtc&startDate=2019-01-02&resampleFreq=5min
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.3.1 Overview

Tiingo connects to a variety of cryptocurrency exchanges to create a consolidated top-of-book feed.

The Benefits of Tiingo Crypto

- Over 8,000 tickers covered, with more added frequently.
- You can choose which exchanges to get data from, and Top-of-Book and aggregate OHLCV data are then compiled on-the-fly.
- Data includes Top-of-Book (Bid/Ask) and Last Sale (trade) Data.
- Data includes intraday OHLC bar historical data for all your needs.
- Tiingo compiles the data to give you updates when the bid/ask and last price change.
- Tiingo enriches the data, giving you more data for your convenience.
- Download the entire market in one API call.

You can find out about the full product offering on the Product - Crypto page.

### 2.3.2 Real-time & Historical Prices Endpoint

**To download real-time and historical prices, use the following REST endpoints.**

```
# Intraday data for current day or last business day
https://api.tiingo.com/tiingo/crypto/prices

# Historical intraday data
https://api.tiingo.com/tiingo/crypto/prices?tickers=<ticker>&startDate=2019-01-02&resampleFreq=5min
```

**Response**

The Crypto Prices endpoint returns meta information about the crypto pair along with the price data related to that pair. This is different tham the other APIs and helps with crypto currency ambiguity.

Check out the table below to see the top-level fields returned from the prices crypto API.

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `baseCurrency` | Base Currency | string | The base pair of the cryptocurrency, e.g. "btc" for "btcusd". |
| `quoteCurrency` | Quote Currency | string | The quote pair of the cryptocurrency, e.g. "usd" for "btcusd". |
| `priceData` | Price Data | object | Stores the top-of-book data being returned. See the table below for the fields in this object. |
| `exchangeData` | Exchange Data | object | The underlying data for each exchange. This will only be returned if the includeRawExchangeData flag is set to "true". |

To see what fields are returned in the "priceData" field, please see the table below.

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The datetime this data pertains to. It will be a timestamp dependent upon the resampleFreq parameter. For example, if resampleFreq=5min, it will be in 5 min increments. |
| `open` | Open | float | The opening price for the asset on the given date. |
| `high` | High | float | The high price for the asset on the given date. |
| `low` | Low | float | The low price for the asset on the given date. |
| `close` | Close | float | The closing price for the asset on the given date. |
| `tradesDone` | Trade Done | int32 | The number of trades done on the given date. |
| `volume` | Volume | float | The volume done for the asset on the specific date in the base currency. |
| `volumeNotional` | Volume Notional | float | The last size done for the asset on the specific date in the quote currency. The volume done for the asset on the specific date in the quote currency. In mathematical terms: volumeNotional = close * volume |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Tickers | yes | string | The ticker(s) associated with the crypto pair(s). Can either be a single string or an array of strings (string[]). Please note: no more than 100 tickers may be requested at this time. |
| `exchanges` | Exchanges | no | string[] | If you would like to limit the query to a subset of exchanes, pass a comma-separated list of exchanged to select. E.g. "POLONIEX, BINANCE". |
| `startDate` | Start Date | no | datetime | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or later than the startDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | datetime | f startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or less than the endDate (<=). Parameter must be in YYYY-MM-DD format. |
| `resampleFreq` | Resample Freq | no | string | This allows you to set the frequency in which you want data resampled. For example "1hour" would return the data where OHLC is calculated on an hourly schedule. The minimum value is "1min". Units in minutes (min), hours (hour), and days (day) are accepted. Format is # + (min/hour/dau); e.g. "15min", "4hour" or "1day". If no value is provided, defaults to 5min. |

**Examples**

**Response:**

```
[
    {
        "ticker":"btcusd",
        "baseCurrency":"btc",
        "quoteCurrency":"usd",
        "priceData":[
            {
                "open":3914.749407813885,
                "high":3942.374263716895,
                "low":3846.1755315352952,
                "close":3849.1217299601617,
                "date":"2019-01-02T00:00:00+00:00",
                "tradesDone":756.0,
                "volume":339.68131616889997,
                "volumeNotional":1307474.735327181
            }
        ]
    }
]
```

### 2.3.3 Meta Endpoint

**To request meta data for a cryptocurrency, use the following REST endpoints.**

```
# To request meta data for all tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/crypto
    
# To request meta data for a specific tickers, use the following REST endpoint
https://api.tiingo.com/tiingo/crypto?tickers=<ticker>
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `baseCurrency` | Base Currency | string | The base pair of the cryptocurrency, e.g. "btc" for "btcusd". |
| `quoteCurrency` | Quote Currency | string | The quote pair of the cryptocurrency, e.g. "usd" for "btcusd". |
| `name` | Name | string | Full-length name of the asset. |
| `description` | Description | string | Long-form descripton of the asset. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Tickers | no | string | The ticker(s) associated with the crypto pair(s). Can either be a single string or an array of strings (string[]). Please note: no more than 100 tickers may be requested at this time. If no ticker(s) are passed, returns data for all supported crypto pairs. |

**Examples**

**Response:**

```
[
    {
        "ticker":"curebtc",
        "baseCurrency":"cure",
        "name":"CureCoin (CURE/BTC)",
        "quoteCurrency":"btc",
        "description":"CureCoin (CURE/BTC)"
    }
]
    
```

### 2.3.4 Top-of-Book Endpoint

**To request top-of-book data, use the following REST endpoints.**

```
# Top-of-book data
https://api.tiingo.com/tiingo/crypto/top?tickers=<ticker>
```

**Response**

The Crypto Top-of-Book endpoint returns meta information about the crypto pair along with the top-of-book data related to that pair. This is different tham the other APIs and helps with crypto currency ambiguity.

Check out the table below to see the top-level fields returned from the top-of-book crypto API.

**Deprecation Warning:** After much consideration, we have made the decision to deprecate the top-of-book endpoint. This is the first endpoint we've fully deprecated in our firm's eight year history, and we do not make this decision lightly. Right now the crypto exchange feeds have been found unreliable to properly construct the best bid/ask in a way that's consistent. Some feeds remain inconsistent, timestamps are sometimes incorrectly stated, and other times it appears as if the messages may not be consistently ordered where the best bid/ask can be constructed. While this task can be done, we believe it must be done with a subset of specific exchanges and encourage clients who need full order-book construction to build this in-house with exchanges that are relevant to you. **If you need last price, that is available on the /prices endpoint described in the above section.** Our philosophy is that no data is better than bad data, and we believe for now, the below task remains too onerous to be done with all 60+ exchanges we cover in a way that properly handles each exchange's potential quirks, let alone when the quirks are constantly changing. We believe Crypto exchanges will eventually converge to equity/futures level reliability, but right now the technology remains early. We will constantly be monitoring and deciding if we want to re-enable the top-of-book endpoint.

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker related to the asset. |
| `baseCurrency` | Base Currency | string | The base pair of the cryptocurrency, e.g. "btc" for "btcusd". |
| `quoteCurrency` | Quote Currency | string | The quote pair of the cryptocurrency, e.g. "usd" for "btcusd". |
| `topOfBookData` | Top-of-Book Data | object | Stores the top-of-book data being returned. See the table below for the fields in this object. |
| `exchangeData` | Exchange Data | object | The underlying data for each exchange. This will only be returned if the includeRawExchangeData flag is set to "true". |

To see what fields are returned in the "topOfBookData" field, please see the table below.

| Field | Name | Type | Description |
|---|---|---|---|
| `quoteTimestamp` | Quote Timestamp | datetime | The timestamp the last time the quote (bid/ask) data was received from any crypto exchange. |
| `lastSaleTimestamp` | Last Sale Timestamp | datetime | The timestamp the last time the trade (last/lastSize) data was received from any crypto exchange. |
| `lastPrice` | Last Price | float | Last price is the last price that was executed on any crypto exchange. |
| `lastSize` | Last Size | float | The amount of crypto volume done at the last price in the base currency. |
| `lastSizeNotional` | Last Size Notional | float | The last size done for the asset on the specific date in the quote currency. The notional of the volume traded. In mathematical terms: lastSizeNotional = lastPrice * lastSize |
| `lastExchange` | Last Exchange | string | The full name of the exchange the "lastPrice" and "lastSize" were executed on. |
| `bidSize` | Bid Size | float | The amount of shares at the bid price. |
| `bidPrice` | Bid Price | float | The current bid price. |
| `bidExchange` | Bid Exchange | string | The full name of the exchange the "bidPrice" and "bidSize" are located on. |
| `askSize` | Ask Size | float | The amount of shares at the ask price. |
| `askPrice` | Ask Price | float | The current ask price. |
| `askExchange` | Ask Exchange | string | The full name of the exchange the "askPrice" and "askSize" are located on. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Tickers | yes | string | The ticker(s) associated with the crypto pair(s). Can either be a single string or an array of strings (string[]). Please note: no more than 100 tickers may be requested at this time. |
| `exchanges` | Exchanges | no | string[] | If you would like to limit the query to a subset of exchanes, pass a comma-separated list of exchanged to select. E.g. "POLONIEX, BINANCE". |
| `includeRawExchangeData` | Include Exchange Data | no | boolean | If True, the endpoint will return all of the underlying exchange data used in the calculation. If this is set to true, there will be a new field called exchangeData for each crypto. This will contain the exchange-level OHLC for each asset. |

**Examples**

**Response:**

```
[
    {
        "ticker":"curebtc",
        "baseCurrency":"cure",
        "quoteCurrency":"btc",
        "topOfBookData":[
            {
                "askSize":21.55601545,
                "bidSize":726.29848588,
                "lastSaleTimestamp":"2019-01-30T00:19:34.777000+00:00",
                "lastPrice":1.894e-05,
                "askPrice":1.9e-05,
                "quoteTimestamp":"2019-01-30T00:44:34.209957+00:00",
                "bidExchange":"BITTREX",
                "lastSizeNotional":0.0010885247347072,
                "lastExchange":"BITTREX",
                "askExchange":"BITTREX",
                "bidPrice":1.894e-05,
                "lastSize":57.47226688
            }
        ]
    }
]
    
```
