# 24x5 Consolidated Real-time WebSocket API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-websockets-equity-realtime-stock-data`).

**WebSocket Endpoints**

Recommended for new integrations

The 24x5 Consolidated Real-time WebSocket covers Sunday 8:00 PM ET through Friday 8:00 PM ET and is included on every plan. The IEX WebSocket remains supported for existing integrations. See the product overview to compare with IEX and BOATS.

```
# Websocket Consolidated Equity Reference Price & Liquidity Risk Metric Endpoint
wss://api.tiingo.com/equity/intraday
```

Just remember, you will need your token in order to connect. Keep it safe.

.

## 3.3.1 Overview

Tiingo provides consolidated equity websocket updates from Tiingo's connections into multiple exchanges, ATS, and OTC venues. The feed combines multiple US equity market data sources into one stream and exposes two derived products: Reference Prices and Liquidity Bid/Ask metrics. These are accessible via two different `thresholdLevel` subscriptions.

The stream runs 24x5, from Sunday 8:00 PM ET through Friday 8:00 PM ET: 4:00 AM to 8:00 PM ET daytime and extended hours, plus 8:00 PM to 3:59 AM ET overnight coverage Sunday through Thursday.

- **thresholdLevel 6** publishes reference price updates when Tiingo detects a meaningful consolidated reference price change.
- **thresholdLevel 4** publishes liquidity spread (bid/ask) updates, including `lqSpread` and the related `lq*` bid/ask fields (price and sizes).

The liquidity risk metric is a statistical estimate of an asset's expected bid/ask spread and liquidity at top-of-book. Tiingo gathers spread and liquidity observations from multiple venues - including exchanges, ATS, and OTC venues - and applies a proprietary methodology to produce an estimated liquidity spread and liquidity bid/ask profile for each security.

For REST snapshots and historical intraday bars, see the 24x5 Consolidated Real-time REST documentation.

### 3.3.2 Reference Price (thresholdLevel 6)

Use `thresholdLevel` 6 to receive consolidated reference price updates. Each message contains a timestamp, ticker, and reference price. This reference price corresponds to `tngoLast` in the REST API and is calculated from validated consolidated quote mids or trade prints. These values are used to create OHLC bars in the REST endpoints.

- A `thresholdLevel` of **6** means you receive reference price updates when Tiingo detects a meaningful consolidated reference price change.

**To request consolidated reference price data, use the following Websocket endpoint.**

```
# Websocket Consolidated Equity Reference Price Endpoint
wss://api.tiingo.com/equity/intraday
```

**Response**

The websocket returns meta information about the update message along with the compact reference price array.

| Field | Name | Type | Description |
|---|---|---|---|
| `service` | Service code | string | An identifier telling you this is consolidated equity data. The value returned by this will always be "cons". |
| `messageType` | Message Type | char | A value telling you what kind of data packet this is. Will always return "A" meaning new price quotes. |
| `data` | Consolidated Reference Price Data | array | An array containing the consolidated reference price data. See the table below for the fields returned in the array. |

To see what fields are returned in the `data` field, please see the table below.

#### Reference Price Update Messages

| Field | Name | Type | Description |
|---|---|---|---|
|  | Date | datetime | A string representing the datetime this reference price update was published, in JSON ISO format. |
|  | Ticker | string | Ticker related to the asset. |
|  | Reference Price | float | Tiingo's consolidated reference price for the asset. This is calculated from validated consolidated quote mids or trade prints across enabled equity venues and corresponds to tngoLast in the REST API. |

**Request**

To subscribe to the Websocket API you must first open a connection then send the following request parameters. Check out the "Examples" tab to see copy & paste examples of how this works.

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `eventName` | Event Name | yes | string | This will be either "subscribe" or "unsubscribe". |
| `authorization` | Authorization | yes | string | This will be your API Auth token. |
| `eventData` | Event Data | no | object | A JSON object passed to the websocket that contains subscribe/unsubscribe parameters. This lets you set the "thresholdLevel". See the table below for specifics. |

The following parameters are what can be passed in the `eventData` field.

#### "eventData" Request Parameters

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `thresholdLevel` | Threshold Level | yes | string | A threshold level. This is a filter that determines how much data you get from the consolidated equity feed. ; 6 - Receive consolidated reference price updates when Tiingo detects a meaningful reference price change. |

**Examples**

**Response:**

```
{
    "messageType":"A",
    "service":"cons",
    "data":[
        "2019-01-30T13:33:45.383129126-05:00",
        "aapl",
        162.33
    ]
}
{
    "messageType":"A",
    "service":"cons",
    "data":[
        "2019-01-30T13:33:45.594808294-05:00",
        "spy",
        274.595
    ]
}
    
```

### 3.3.3 Liquidity Risk Metric (thresholdLevel 4)

Use `thresholdLevel` 4 to receive liquidity spread updates. Each message contains a timestamp, ticker, liquidity spread, liquidity bid size, liquidity bid price, reference price, liquidity ask price, and liquidity ask size.

The payload shape is `[timestamp, ticker, lqSpread, lqBidSize, lqBidPrice, referencePrice, lqAskPrice, lqAskSize]`. `lqSpread` is a relative decimal (i.e. where 0.04 means 4%). The remaining liquidity fields correspond to the `lq*` fields exposed on the consolidated equity REST endpoint, e.g. lqBidPrice and lqAskPrice. These are expanded upon below. These metrics are intended to be used as valuation and risk metrics to help value assets in real-time.

- A `thresholdLevel` of **4** means you receive liquidity risk metric updates when Tiingo republishes the statistical liquidity estimate for a security.

**To request liquidity risk metric data, use the following Websocket endpoint.**

```
# Websocket Consolidated Equity Liquidity Risk Metric Endpoint
wss://api.tiingo.com/equity/intraday
```

**Response**

The websocket returns meta information about the update message along with the compact liquidity risk metric array.

| Field | Name | Type | Description |
|---|---|---|---|
| `service` | Service code | string | An identifier telling you this is consolidated equity data. The value returned by this will always be "cons". |
| `messageType` | Message Type | char | A value telling you what kind of data packet this is. Will always return "A" meaning a new liquidity risk metric update. |
| `data` | Consolidated Liquidity Risk Metric Data | array | An array containing the statistical liquidity risk metric data. See the table below for the fields returned in the array. |

To see what fields are returned in the `data` field, please see the table below.

#### Liquidity Risk Metric Update Messages

| Field | Name | Type | Description |
|---|---|---|---|
|  | Date | datetime | A string representing the datetime this liquidity risk metric update was published, in JSON ISO format. |
|  | Ticker | string | Ticker related to the asset. |
|  | Liquidity Spread | float | The relative spread width component of the liquidity risk metric, expressed as a decimal where 0.04 means 4%. Corresponds to lqSpread in the REST API. |
|  | Liquidity Bid Size | int32 | The bid size component of the liquidity risk metric in shares. Corresponds to lqBidSize in the REST API. |
|  | Liquidity Bid Price | float | The bid price component of the liquidity risk metric. Corresponds to lqBidPrice in the REST API. |
|  | Reference Price | float | The reference price used as the center of the liquidity risk metric. This is normally the threshold-level 6 reference price (tngoLast) when available. |
|  | Liquidity Ask Price | float | The ask price component of the liquidity risk metric. Corresponds to lqAskPrice in the REST API. |
|  | Liquidity Ask Size | int32 | The ask size component of the liquidity risk metric in shares. Corresponds to lqAskSize in the REST API. |

**Request**

To subscribe to the Websocket API you must first open a connection then send the following request parameters. Check out the "Examples" tab to see copy & paste examples of how this works.

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `eventName` | Event Name | yes | string | This will be either "subscribe" or "unsubscribe". |
| `authorization` | Authorization | yes | string | This will be your API Auth token. |
| `eventData` | Event Data | no | object | A JSON object passed to the websocket that contains subscribe/unsubscribe parameters. This lets you set the "thresholdLevel". See the table below for specifics. |

The following parameters are what can be passed in the `eventData` field.

#### "eventData" Request Parameters

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `thresholdLevel` | Threshold Level | yes | string | A threshold level. This is a filter that determines how much data you get from the consolidated equity feed. ; 4 - Receive liquidity risk metric updates — statistical estimates of spread and liquidity characteristics derived from aggregated historical observations. |

**Examples**

**Response:**

```
{
    "messageType":"A",
    "service":"cons",
    "data":[
        "2019-01-30T13:33:45.383129126-05:00",
        "aapl",
        0.0011,
        100,
        162.30,
        162.33,
        162.36,
        100
    ]
}
{
    "messageType":"A",
    "service":"cons",
    "data":[
        "2019-01-30T13:33:45.594808294-05:00",
        "spy",
        0.0008,
        100,
        274.57,
        274.595,
        274.62,
        100
    ]
}
    
```
