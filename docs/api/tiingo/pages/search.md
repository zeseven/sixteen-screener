# Search API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-search`).

**REST Endpoints**

Search Endpoint has just been launched and is in early beta. Responses objects are subject to change. We do not recommend building production code using this endpoint while in beta.

```
# Search Endpoint
https://api.tiingo.com/tiingo/utilities/search/<query>
# or
https://api.tiingo.com/tiingo/utilities/search?query=<query>
```

Just remember, you will need your token in order to connect. Keep it safe.

.

## 4.1.1 Overview

Tiingo's search feature lets you find specific assets in our database by the ticker or the name of the asset. This endpoint lets you segment by active, delisted, tickers across asset classes. The endpoint first searches for ticker matches and then expands to matches in the name of the asset.

This endpoint is useful for looking up existing assets

### 4.1.2 Search Endpoint

**To request search query data, use the following REST endpoints.**

```
# Search Tiingo database for specific assets
https://api.tiingo.com/tiingo/utilities/search/apple
# or
https://api.tiingo.com/tiingo/utilities/search?query=apple
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `ticker` | Ticker | string | Ticker of the given asset. |
| `name` | Name | string | The name of the asset. |
| `assetType` | Asset Type | string | The asset type of the asset (Stock, ETF, & Mutual Fund). |
| `isActive` | Is Active | boolean | True if the ticker is still actively quoted, and false if the ticker is no longer actively quoted (delisted). |
| `permaTicker` | Tiingo PermaTicker | string | Placeholder for an upcoming change to the Tiingo API that allows querying by permaticker. |
| `openFIGI` | OpenFIGI Ticker | string | Placeholder for an upcoming change to the Tiingo API that allows querying by the openFIGI ticker. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `query` | Search Query | yes | string | The search query to use to look up assets. |
| `exactTickerMatch` | Exact Ticker Match | no | boolean | True to only include exact ticker matches based on the search query. If set to true, no partial matches will be included and asset names will not be searched. |
| `includeDelisted` | Include Delisted | no | boolean | True to include delisted tickers and false (default) to exclude delisted tickers. |
| `limit` | Limit | no | int32 | The maximum number of assets to return. Defaults to 10 and can be set to a maximum of 100. |

**Examples**

**Response:**

```
[
    {
    "ticker":"AAPL",
    "assetType":"Stock",
    "countryCode":"US",
    "isActive":true,
    "name":"Apple Inc",
    "openFIGI":"BBG000B9XRY4",
    "permaTicker":"US000000000038"
    },
    {
    "ticker":"PNPL",
    "assetType":"Stock",
    "countryCode":"US",
    "isActive":true,
    "name":"Pineapple Exprss",
    "openFIGI":null,
    "permaTicker":"US000000047877"
    },
..]
    
```
