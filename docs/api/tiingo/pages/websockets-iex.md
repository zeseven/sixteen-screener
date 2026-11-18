# Real-time IEX WebSocket API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-websockets-iex`).

**WebSocket Endpoints**

We recommend switching to 24x5

Our new 24x5 Consolidated Real-time feed is the successor to IEX Derived - one consolidated price and liquidity spread per stock from multiple venues, Sunday 8:00 PM ET through Friday 8:00 PM ET, included on every plan. IEX Derived is still included, so existing integrations keep working. Start with the 24x5 WebSocket docs.

```
# Websocket IEX API Reference Price, Top-of-Book, & Last Trade Endpoint
wss://api.tiingo.com/iex
```

Just remember, you will need your token in order to connect. Keep it safe.

.

## 3.4.1 Overview

Tiingo provides updates via websocket every time the Top-of-book (best bid/offer prices and sizes) change and when a trade is executed.

We obtain our data through raw binary feeds we receive via a physical connection to IEX in the NY5 data center. We then send the data straight from IEX to you after minor processing (even before we update our databases). This means we send you the IEX data as a JSON array within our JSON object.

To further minimize latency, we use bare metal machines for our cloud infrastructure which are located about 15 miles from the NY5 data center.

- Trades are when a security/stock was traded on an exchange and includes the price (lastPrice) and the volume done (lastSize).
- A quote update is when the bid/ask changes, but no trades were done. Tiingo sends a Quote update message via the Websocket if the Top-of-Book values change. In other words, if the best bid price, best bid size, best ask price, or best ask size change, then an update is sent.

With Tiingo Free, Power, Commercial, and Redistribution plans all come with access to the firehose. Please note the firehose exposes a very high amount of data, in some cases to the nanosecond resolution. Please build your systems cautiously and to scale, otherwise you may use our REST API which leverages Tiingo's infrastructure for this purpose.

**As of February 1st, 2025** IEX Exchange has changed their market data policies. To receive the FULL TOPS Feed, you must now have a market data agreement signed with the IEX Exchange. Upon signing, you will then be able to receive the full TOPS feed in real-time. If you want a thresholdLevel of 0 or 5 as described below, you will need this agreement.

For customers who do not want to sign a license agreement, you may use our derived data that calculates a reference price for each asset in real-time. While this is not a subsitute for the TOPS Feed, we do believe it will fulfill the needs of 95% of our customer base. There is no additional cost to the IEX Exchange if using our derived data. If you want this compliant-friendly reference price, you may use a thresholdLevel of 6 as described below.

You can find out about the full product offering on the Product - IEX page.

### 3.4.2 Reference Price (Derived Data Calculation)

If you do not want to formally paper an agreement with the exchange, you may use the Tiingo Reference price calculation. This calculation is not a full substitute for the TOPS feed, but should fulfill 95% of our customer use cases that require real-time price data. In fact, we even encourage customers who register with the exchange to still use this calculation as it will result in fuller charts and more frequent price updates.

If you do need the full IEX TOPS feed, please scroll below to section **3.4.3**

For the Tiingo Reference Price Websocket API:

- A "thresholdLevel" of 6 means you updates price updates when a reference price change is detected.

**To request top-of-book and last trade data, use the following Websocket endpoint.**

```
# Websocket Top-of-Book & Last Trade Endpoint
wss://api.tiingo.com/iex
```

**Response**

The websocket returns meta information about the websocket update message along with the raw data related to that update message.

Check out the table below to see the top-level fields returned from the Tiingo Websocket IEX Reference Price API.

| Field | Name | Type | Description |
|---|---|---|---|
| `service` | Service code | string | An identifier telling you this is IEX data. The value returned by this will always be "iex". |
| `messageType` | Message Type | char | A value telling you what kind of data packet this is from our IEX feed. Will always return "A" meaning new price quotes. |
| `data` | Tiingo Reference Price Data | array | An array containing the reference data. See the tables below to see the fields returned in the array. |

To see what fields are returned in the "data" field, please see the table below.

#### Reference Price Update Messages

| Field | Name | Type | Description |
|---|---|---|---|
|  | Date | datetime | A string representing the datetime this quote or trade came in. This is the timestamp reported by IEX in JSON ISO Format. |
|  | Ticker | string | Ticker related to the asset. |
|  | Reference Price | float | The rerence price calculated using an internal algorithm to determine a fair reference price for the asset based on underlying quote and trade messages. This can be used as you would use tngoLast in the REST API. |

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
| `thresholdLevel` | Threshold Level | yes | string | A threshold level. This is a filter that determines how much data you get from the IEX feed. ; 6 Receive all Tiingo Reference price messages. |

**Examples**

**Response:**

```
{
    "messageType":"A",
    "service":"iex",
    "data":[
        "2019-01-30T13:33:45.383129126-05:00",
        "vym",
        81.585
    ]
}
{
    "messageType":"A",
    "service":"iex",
    "data":[
        "2019-01-30T13:33:45.594808294-05:00",
        "wes",
        50.285
    ]
}
    
```

The below example shows how you can subscribe to a few tickers and then update the subscription to add or remove new tickers to the list.

First you can create a subscription with the "tickers" parameter. If you pass "*" as a ticker, it will mean you will get data for ALL tickers.

**Response:**

```
{
    "messageType":"I",
    "data": {
        "subscriptionId":13706
    },
    "response": {
        "code":200,
        "message":"Success"
    }
}
{
    "messageType":"H",
    "response": {
        "code":200,
        "message":"HeartBeat"
    }
}
{
    "messageType":"A",
    "service":"iex"
    "data":[
        "2019-02-14T12:17:19.342553795-05:00",
        "spy",
        274.595
    ],
}
{
    "messageType":"A",
    "service":"iex"
    "data":[
        "2019-02-14T12:17:20.105597077-05:00",
        "uso",
        11.395,
    ]
}
    
```

### 3.4.3 Top-of-Book & Last Trade

With Tiingo's Websocket/Firehose IEX API, you can gain access to all data we receive via the cross connect, or to data our system determines is a major update.

To control how much data you would like to receive, read about the "thresholdLevel" request parameter below. A higher "thresholdLevel" means you will get less updates, which could potentially be more relevant.

For the IEX Websocket API:

- A "thresholdLevel" of 0 means you will get ALL Top-of-Book AND Last Trade updates.
- A "thresholdLevel" of 5 means you will get all Last Trade updates and only Quote updates that are deemed major updates by our system.

**To request top-of-book and last trade data, use the following Websocket endpoint.**

```
# Websocket Top-of-Book & Last Trade Endpoint
wss://api.tiingo.com/iex
```

**Response**

The IEX websocket returns meta information about the websocket update message along with the raw data related to that update message.

Check out the table below to see the top-level fields returned from the Websocket IEX API.

| Field | Name | Type | Description |
|---|---|---|---|
| `service` | Service code | string | An identifier telling you this is IEX data. The value returned by this will always be "iex". |
| `messageType` | Message Type | char | A value telling you what kind of data packet this is from our IEX feed. Will always return "A" meaning new price quotes. |
| `data` | IEX TOP/Price Data | array | An array containing the IEX data. See the tables below to see the fields returned in the array depending if the message is a "Trade" or "TOP" Update message. |

To see what fields are returned in the "data" field, please see the table below.

#### Trade & Top-of-Book (Quote) Update Messages

| Field | Name | Type | Description |
|---|---|---|---|
|  | Update Message Type | char | Communicates what type of price update this is. Will always be "T" for last trade message, "Q" for top-of-book update message, and "B" for trade break messages. |
|  | Date | datetime | A string representing the datetime this quote or trade came in. This is the timestamp reported by IEX in JSON ISO Format. |
|  | Nanoseconds | int64 | An integer representing the number of nanoseconds since POSIX (Epoch) time UTC. |
|  | Ticker | string | Ticker related to the asset. |
|  | Bid Size | int32 | The number shares at the bid price. Only available for Quote updates, null otherwise. |
|  | Bid Price | float | The current highest bid price. Only available for Quote updates, null otherwise. |
|  | Mid Price | float | The mid price of the current timestamp when both "bidPrice" and "askPrice" are not-null. In mathematical terms: mid = (bidPrice + askPrice)/2.0 This value is calculated by Tiingo and not provided by IEX. Only available for Quote updates, null otherwise. |
|  | Ask Price | float | The current lowest ask price. Only available for Quote updates, null otherwise. |
|  | Ask Size | int32 | The number of shares at the ask price. Only available for Quote updates, null otherwise. |
|  | Last Price | float | The last price the last trade was executed at. Only available for Trade and Break updates, null otherwise. |
|  | Last Size | int32 | The amount of shares (volume) traded at the last price. Only available for Trade and Break updates, null otherwise. |
|  | Halted | int32 | 1 if the security/asset is halted, 0 if it is not halted (this comes from IEX). |
|  | After Hours | int32 | 1 if the data is after hours, 0 if the update was during market hours (this comes from IEX). |
|  | Intermarket Sweep Order (ISO) | int32 | 1 if the order is an intermarket sweep order (ISO) sweeping order, 0 if its a non-ISO order (this comes from IEX). |
|  | Oddlot | int32 | 1 if the trade is an odd lot, 0 if the trade is a round or mixed lot (this comes from IEX). Only available for Trade updates, null otherwise. |
|  | NMS Rule 611 | int32 | 1 if the trade is not subject to NMS Rule 611 (trade through), 0 if the trade is subject to Rule NMS 611 (this comes from IEX). Only available for Trade updates, null otherwise. |

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
| `thresholdLevel` | Threshold Level | yes | string | A threshold level. This is a filter that determines how much data you get from the IEX feed. ; 0 - All updates from IEX. Be careful with this as it is A LOT of data. ; 5 - If a quote: sends updates where mid is not null AND there is a change in mid by at least $0.01 OR If a trade: send a trade if it differs from the last trade price. |

**Examples**

**Response:**

```
{
    "messageType":"A",
    "service":"iex",
    "data":[
        "Q",
        "2019-01-30T13:33:45.383129126-05:00",
        1548873225383129126,
        "vym",
        100,
        81.58,
        81.585,
        81.59,
        100,
        null,
        null,
        0,
        0,
        null,
        null,
        null
    ]
}
{
    "messageType":"A",
    "service":"iex",
    "data":[
        "T",
        "2019-01-30T13:33:45.594808294-05:00",
        1548873225594808294,
        "wes",
        null,
        null,
        null,
        null,
        null,
        50.285,
        200,
        null,
        0,
        0,
        0,
        0
    ]
}
    
```

The below example shows how you can subscribe to a few tickers and then update the subscription to add or remove new tickers to the list.

First you can create a subscription with the "tickers" parameter. If you pass "*" as a ticker, it will mean you will get data for ALL tickers.

**Response:**

```
{
    "messageType":"I",
    "data": {
        "subscriptionId":13706
    },
    "response": {
        "code":200,
        "message":"Success"
    }
}
{
    "messageType":"H",
    "response": {
        "code":200,
        "message":"HeartBeat"
    }
}
{
    "messageType":"A",
    "service":"iex"
    "data":[
        "Q",
        "2019-02-14T12:17:19.342553795-05:00",
        1550164639342553795,
        "spy",
        100,
        274.58,
        274.595,
        274.61,
        100,
        null,
        null,
        0,
        0,
        null,
        null,
        null
    ],
}
{
    "messageType":"A",
    "service":"iex"
    "data":[
        "Q",
        "2019-02-14T12:17:20.105597077-05:00",
        1550164640105597077,
        "uso",
        20000,
        11.39,
        11.395,
        11.4,
        3000,
        null,
        null,
        0,
        0,
        null,
        null,
        null
    ]
}
    
```

Use the **subscriptionId** from the prior response to update the subscription. Specify the **subscriptionId** in the **eventData** parameter.

This command should be run as a second process. This means you can edit the first concurrent subscription without having to stop the flow of data packets.

The below example removes "spy" as one of the subscribed tickers. You will be returned a list of currently subscribed tickers, in the response, if the action is successful.

**Response:**

```
{
    "data": {
        "tickers":[
            "uso"
        ],
        "thresholdLevel":"0"
    },
    "messageType":"I",
    "response": {
        "code":200,
        "message":"Success"
    }
}
    
```

The below examples add all tickers ("*") as one of the subscribed tickers. You will be returned a list of currently subscribed tickers, in the response, if the action is successful.

**Response:**

```
{
    "data": {
        "tickers":[
            "*",
            "uso"
        ],
        "thresholdLevel":"0"
    },
    "messageType":"I",
    "response": {
        "code":200,
        "message":"Success"
    }
}
    
```
