# Real-time Crypto WebSocket API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-websockets-crypto`).

**WebSocket Endpoints**

```
# Websocket Crypo API Top-of-Book & Last Trade Endpoint
wss://api.tiingo.com/crypto
```

Just remember, you will need your token in order to connect. Keep it safe.

.

## 3.1.1 Overview

Tiingo provides updates via websocket every time the Top-of-book (best bid/offer prices and sizes) change and when a trade is executed.

Exchanges often send out quotes for both trades and quote updates.

- Trades are when a security/stock was traded on an exchange and includes the price (lastPrice) and the volume done (lastSize).
- A quote update is when the bid/ask changes, but no trades were done. Tiingo sends a Quote update message via the Websocket if the Top-of-Book values change. In other words, if the best bid price, best bid size, best ask price, or best ask size change, then an update is sent.

With Tiingo Free, Power, Commercial, and Redistribution plans all come with access to the firehose. Please note the firehose exposes a very high amount of data, in some cases to the nanosecond resolution. Please build your systems cautiously and to scale, otherwise you may use our REST API which leverages Tiingo's infrastructure for this purpose.

You can find out about the full product offering on the Product - Crypto page.

### 3.1.2 Top-of-Book & Last Trade

With Tiingo's Websocket/Firehose Crypto API, you can gain access to either just last trade data, or to both top-of-book and last trade updates. The top-of-book updates are offered at the per-exchange level, so you will see the top-of-book stream for a ticker offered on a specific exchange.

To control how much data you would like to receive, read about the "thresholdLevel" request parameter below. A higher "thresholdLevel" means you will get less updates, which could potentially be more relevant.

For the Crypto Websocket API:

- A "thresholdLevel" of 2 means you will get Top-of-Book AND Last Trade updates.
- A "thresholdLevel" of 5 means you will get only Last Trade updates

**To request top-of-book and last trade data, use the following Websocket endpoint.**

```
# Websocket Top-of-Book & Last Trade Endpoint
wss://api.tiingo.com/crypto
```

**Response**

The Crypto websocket returns meta information about the websocket update message along with the raw data related to that update message.

Check out the table below to see the top-level fields returned from the Websocket crypto API.

| Field | Name | Type | Description |
|---|---|---|---|
| `service` | Service code | string | An identifier telling you this is crypto data. The value returned by this will always be "crypto_data". |
| `messageType` | Message Type | char | A value telling you what kind of data packet this is from our crypto feed. Will always return "A" meaning new price quotes. |
| `data` | Crypto TOP/Price Data | array | An array containing the crypto data. See the tables below to see the fields returned in the array depending if the message is a "Trade" or "TOP" Update message. |

To see what fields are returned in the "data" field, please see the table below.

#### Trade Update Message

| Field | Name | Type | Description |
|---|---|---|---|
|  | Update Message Type | char | Communicates what type of price update this is. Will always be "T" for last trade message and "Q" for top-of-book update message. |
|  | Ticker | string | Ticker related to the asset. |
|  | Date | datetime | A string representing the datetime this trade quote came in. |
|  | Exchange | string | The exchange the trade was done one. |
|  | Last Size | float | The amount of crypto volume done at the last price in the base currency. |
|  | Last Price | float | The last price the last trade was executed at. |

#### Top-of-Book (Quote) Update Message

| Field | Name | Type | Description |
|---|---|---|---|
|  | Update Message Type | char | Communicates what type of price update this is. Will always be "T" for last trade message and "Q" for top-of-book update message. |
|  | Ticker | string | Ticker related to the asset. |
|  | Date | datetime | A string representing the datetime this trade quote came in. |
|  | Exchange | string | The exchange the top-of-book update is for. |
|  | Bid Size | float | The amount of crypto at the bid price in the base currency. |
|  | Bid Price | float | The current highest bid price. |
|  | Mid Price | float | The mid price of the current timestamp when both "bidPrice" and "askPrice" are not-null. In mathematical terms: mid = (bidPrice + askPrice)/2.0 |
|  | Ask Size | float | The amount of crypto at the ask price in the base currency. |
|  | Ask Price | float | The current lowest ask price. |

**Request**

To subscribe to the Websocket API you must first open a connection then send the following request parameters. Check out the "Examples" tab to see copy & paste examples of how this works.

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `eventName` | Event Name | yes | string | This will be either "subscribe" or "unsubscribe". |
| `authorization` | Authorization | yes | string | This will be your API Auth token. |
| `eventData` | Event Data | no | object | A JSON object passed to the websocket that contains subscribe/unsubscribe parameters. This lets you set the "thresholdLevel". See the table below for specifics. |

The following parameters are what can be passed in the "eventData" field.

#### "eventData" Request Parameters

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `subscriptionId` | Subscription ID | no | string | This is used to modify existing subscriptions. Leave this blank for new connections. Can use this parameter to subscribe/unsubscribe from additional tickers. |
| `thresholdLevel` | Threshold Level | no | string | A threshold level. This is a filter that determines how much data you get from the crypto feed. ; 2 - Top-of-Book quote updates as well as Trade updates. Both quote and trade updates are per-exchange ; 5 - Trade Updates per-exchange. |

**Examples**

**Response:**

```
{
    "messageType":"A",
    "service":"crypto_data",
    "data":[
        "Q",
        "neojpy",
        "2019-01-30T18:03:40.195515+00:00",
        "bitfinex",
        38.11162867,
        787.82,
        787.83,
        42.4153887,
        787.84
    ]
}
{
    "messageType":"A",
    "service":"crypto_data",
    "data":[
        "T",
        "evxbtc",
        "2019-01-30T18:03:40.056000+00:00",
        "binance",
        405.0,
        9.631e-05
    ]
}
    
```
