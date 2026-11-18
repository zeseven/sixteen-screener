# Cryptocurrency Yield Lending API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-crypto-yield`).

**REST Endpoints**

```
# To gather a list of all Yield Platforms Supported (e.g. AAVEV2, COMPOUND, etc.)
https://api.tiingo.com/tiingo/crypto-yield/platforms

# To request meta data for all yield pools, use the following REST endpoint
https://api.tiingo.com/tiingo/crypto-yield/pools

# Yield Pool Tick Metrics
https://api.tiingo.com/tiingo/crypto-yield/ticks

# Yield Pool Historical OHLC Metrics
https://api.tiingo.com/tiingo/crypto-yield/<poolCode>/metrics
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.4.1 Overview

Tiingo reads yield data directly on chain from various Cryptocurrency lending platforms such as AAVE, Compound, BenQi, Solend, and MakerDAO.

The Benefits of Tiingo Crypto Yield

- Pools and platforms easily queryable and yields and notional borrowed/supplied are aggregated in OHLCV and tick form.
- Complicated yield aggregations are all standardized into the Tiingo API for easy and accurate acquisition of data.
- Data is broken out into stable and variable yields if supported by the lending platforms.
- Notionals borrowed and supplied are provided.
- Data is updated constantly as changes are detect on blockchains.
- Multiple blockchains and networks are supported.
- Data can be downloaded in raw bulk form for enterprise customers..

To understand the relationship between Yield Lending Platforms and Yield Pools on the Tiingo API, think of the relationship of Exchanges and Cryptocurrencies.

Yield Platforms are similar to exchanges, and Yield Pools are similar to Cryptocurrencies in that they are mapped to specific Yield Platforms. E.g. "aavev2_usdc" Yield Pool is listed on the AAVE Yield Platform.

You can use the Yield Platform and Yield Pool meta endpoints to determine which pools belong on which platforms, then use the **poolCode** to query specific lending information from each Yield Pool via the **metrics** and **ticks** endpoints.

### 2.4.2 Yield Platform Meta Data

This endpoint returns the various Platforms supported by Tiingo. The **platformCode** acts as a unique identifier you can use to map Pools to.

**To see a list of supported lending Platforms, use the following REST endpoints.**

```
# To gather a list of all Yield Platforms Supported (e.g. AAVEV2, COMPOUND, etc.)
https://api.tiingo.com/tiingo/crypto-yield/platforms
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `name` | Name | string | Formatted name of the Yield Platform. |
| `platformCode` | Platform Code | string | A unique identifier for the Yield Platform that can be used throughout the API to map Yield Pools to the given Platform. Similar to exchangeCode in the equity endpoints. |
| `network` | Network | string | The network this platform belongs to. E.g. ETH for mainnet, AVAX for Avalanche, SOL, for Solana.". |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `platformCodes` | Platform Codes | no | string | A list of platform codes to constrain the output to the given platforms. If passing it as a string in the URL, it can be a comma-separated list. |

**Examples**

**Response:**

```
[
    {
        "platformCode":"MAKERDAO",
        "name":"Maker DAO",
        "network":"ETH"
    },
    {
        "platformCode":"BENQI",
        "name":"BENQI",
        "network":"AVAX"
    },
    {
        "platformCode":"AAVEV2",
        "name":"AAVE V2",
        "network":"ETH"
    },
]                                    
```

### 2.4.3 Yield Pool Meta Data

Yield pools are pools that belong to a specific Platform. You can treat poolCode similarly as cryptocurrency tickers.

Pool codes will be unique identifiers that you can query tick and metric data from.

**To request meta data for a Yield Pools, use the following REST endpoints.**

```
# To request meta data for all yield pools, use the following REST endpoint
https://api.tiingo.com/tiingo/crypto-yield/pools
    
# To request meta data for a specific yield pools, use the following REST endpoint
https://api.tiingo.com/tiingo/crypto-yield/pools?poolCodes=<poolCodes>

# To request meta data for a specific yield platforms, use the following REST endpoint
https://api.tiingo.com/tiingo/crypto-yield/pools?platformCodes=<platformCodes>
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `name` | Name | string | Name of the yield pool. |
| `poolCode` | Pool Code | string | A unique identifier mapped to this pool that can be used to query metric and tick data throughout the API. |
| `yieldPlatform` | Yield Platform | string | The Yield Platform this pool belongs to. The value will be the platformCode of the corresponding Yield Platform.. |
| `borrowRateDenomination` | Borrow Rate Denimination | string | The cryptocurrency, or token, the borrow rate is denominated in. |
| `totalBorrowDenomination` | Borrow Amount Denomination | string | The cryptocurrency, or token, the borrow amount is denominated in. |
| `supplyRateDenomination` | Supply Rate Denimination | string | The cryptocurrency, or token, the supply rate is denominated in. |
| `totalSupplyDenomination` | Supply Amount Denomination | string | The cryptocurrency, or token, the supply amount is denominated in. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `poolCodes` | Pool Codes | no | string | The pool code(s) associated with the Yield Pool. Can either be a single string or an array of strings (string[]). If no pool code(s) are passed, returns data for all supported yield pools. |
| `platformCodes` | Platform Codes | no | string | The platform code(s) associated with the Yield Pools that you would like to constrain the output by. Can either be a single string or an array of strings (string[]). If no platform code(s) are passed, returns data for all supported yield pools for all platforms. |

**Examples**

**Response:**

```
[
    {
        "name": "benqi_wavax",
        "poolCode": "benqi_wavax",
        "yieldPlatform": "BENQI",
        "borrowRateDenomination": "WAVAX",
        "totalBorrowDenomination": "WAVAX",
        "supplyRateDenomination": "WAVAX",
        "totalSupplyDenomination": "WAVAX"
    },
    {
        "name": "benqi_dai_e",
        "poolCode": "benqi_dai_e",
        "yieldPlatform": "BENQI",
        "borrowRateDenomination": "DAI_E",
        "totalBorrowDenomination": "DAI_E",
        "supplyRateDenomination": "DAI_E",
        "totalSupplyDenomination": "DAI_E"
    },
    {
        "name": "benqi_usdc_e",
        "poolCode": "benqi_usdc_e",
        "yieldPlatform": "BENQI",
        "borrowRateDenomination": "USDC_E",
        "totalBorrowDenomination": "USDC_E",
        "supplyRateDenomination": "USDC_E",
        "totalSupplyDenomination": "USDC_E"
    },
    ...
]                                    
    
```

### 2.4.4 Yield Pool Tick Metrics Endpoint

This endpoint allows you to query the latest metrics for the various yield pools in real-time as our processor reads changes from chain.

If you need historical values, please see the metrics ohlc endpoint below (Section 2.4.5).

**To request yield pool metrics, use the following REST endpoints.**

```
# Yield Pool Metric Ticks
https://api.tiingo.com/tiingo/crypto-yield/ticks

# Yield Pool Metric Ticks filtered by Pool Code
https://api.tiingo.com/tiingo/crypto-yield/ticks?poolCodes=<poolCode>
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The datetime this data pertains to. It will be a timestamp dependent upon the resampleFreq parameter. For example, if resampleFreq=5min, it will be in 5 min increments. |
| `poolCode` | Pool Code | string | A unique identifier mapped to this pool that can be used to query metric and tick data throughout the API. |
| `variableBorrowRate` | Variable Borrow Rate | float | The variable borrow rate (multiply by 100 for percentage). |
| `totalStableBorrowAmount` | Stable Borrow Rate | float | The stable borrow rate (multiply by 100 for percentage). |
| `totalVariableBorrowAmount` | Variable Borrow Amount | float | The amount borrowed subject to the variable borrow rate. |
| `totalBorrowAmount` | Total Borrow Amount | float | The total amount borrowed (total = stable amount + variable amount) |
| `supplyRate` | Supply Rate | float | The supply rate (multiply by 100 for percentage). |
| `totalSupplyAmount` | Total Supply Amount | float | The close total supply amount on the given date. |
| `availableLiquidityAmount` | Available Liquidity Amount | float | The amount of liquidity available that can be borrowed. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `poolCodes` | Pool Codes | no | string | The pool code(s) associated with the Yield Pool. Can either be a single string or an array of strings (string[]). If no pool code(s) are passed, returns data for all supported yield pools. |
| `platformCodes` | Platform Codes | no | string | The platform code(s) associated with the Yield Pools that you would like to constrain the output by. Can either be a single string or an array of strings (string[]). If no platform code(s) are passed, returns data for all supported yield pools for all platforms. |

**Examples**

**Response:**

```
[
    {
        "date":"2022-09-06T20:41:00+00:00",
        "poolCode":"aavev2_wbtc",
        "variableBorrowRate":0.010330771249573633,
        "stableBorrowRate":0.042913464061967034,
        "totalStableBorrowAmount":25.38769672,
        "totalVariableBorrowAmount":2989.73257057,
        "totalBorrowAmount":3015.120199060002,
        "supplyRate":0.0007105381922212847,
        "totalSupplyAmount":35921.0049678,
        "availableLiquidityAmount":32905.88476874
    }
    ...
]
        
```

### 2.4.5 Yield Pool Historical (OHLC) Metrics Endpoint

This endpoint allows you to query metrics for the various yield pools, and resample the data into OHLC values. Data can be resampled to as low as 1 minute bars, and the latest bar will constantly update throughout the minute.

If you need ticks, please see the ticks endpoint below (Section 2.4.5).

**To request yield pool metrics, use the following REST endpoints.**

```
# Yield Pool Metrics
https://api.tiingo.com/tiingo/crypto-yield/<poolCode>/metrics

# Yield Pool Metrics reasmpled to 1 minute bars historically
https://api.tiingo.com/tiingo/crypto-yield/<poolCode>/metrics?startDate=2022-09-04&endDate=2022-09-05&resampleFreq=1min
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `date` | Date | datetime | The datetime this data pertains to. It will be a timestamp dependent upon the resampleFreq parameter. For example, if resampleFreq=5min, it will be in 5 min increments. |
| `openVariableBorrowRate` | Open Variable Borrow Rate | float | The open variable borrow rate on the given date. |
| `highVariableBorrowRate` | High Variable Borrow Rate | float | The high variable borrow rate on the given date. |
| `lowVariableBorrowRate` | Low Variable Borrow Rate | float | The low variable borrow rate on the given date. |
| `closeVariableBorrowRate` | Close Variable Borrow Rate | float | The close variable borrow rate on the given date. |
| `openStableBorrowRate` | Open Stable Borrow Rate | float | The open stable borrow rate on the given date. |
| `highStableBorrowRate` | High Stable Borrow Rate | float | The high stable borrow rate on the given date. |
| `lowStableBorrowRate` | Low Stable Borrow Rate | float | The low stable borrow rate on the given date. |
| `closeStableBorrowRate` | Close Stable Borrow Rate | float | The close stable borrow rate on the given date. |
| `openTotalVariableBorrowAmount` | Open Variable Borrow Amount | float | The open variable borrow amount on the given date. |
| `highTotalVariableBorrowAmount` | High Variable Borrow Amount | float | The high variable borrow amount on the given date. |
| `lowTotalVariableBorrowAmount` | Low Variable Borrow Amount | float | The low variable borrow amount on the given date. |
| `closeTotalVariableBorrowAmount` | Close Variable Borrow Amount | float | The close variable borrow amount on the given date. |
| `openTotalStableBorrowAmount` | Open Stable Borrow Amount | float | The open supply rate on the given date. |
| `highTotalStableBorrowAmount` | High Stable Borrow Amount | float | The high supply rate on the given date. |
| `lowTotalStableBorrowAmount` | Low Stable Borrow Amount | float | The low supply rate on the given date. |
| `closeTotalStableBorrowAmount` | Close Stable Borrow Amount | float | The close supply rate on the given date. |
| `openTotalBorrowAmount` | Open Total Borrow Amount | float | The open total borrow amount on the given date (total = stable amount + variable amount). |
| `highTotalBorrowAmount` | High Total Borrow Amount | float | The high total borrow amount on the given date (total = stable amount + variable amount). |
| `lowTotalBorrowAmount` | Low Total Borrow Amount | float | The low total borrow amount on the given date (total = stable amount + variable amount). |
| `closeTotalBorrowAmount` | Close Total Borrow Amount | float | The close total borrow amount on the given date (total = stable amount + variable amount). |
| `openSupplyRate` | Open Supply Rate | float | The open supply rate on the given date. |
| `highSupplyRate` | High Supply Rate | float | The high supply rate on the given date. |
| `lowSupplyRate` | Low Supply Rate | float | The low supply rate on the given date. |
| `closeSupplyRate` | Close Supply Rate | float | The close supply rate on the given date. |
| `openTotalSupplyAmount` | Open Total Supply Amount | float | The open total supply amount on the given date. |
| `highTotalSupplyAmount` | High Total Supply Amount | float | The high total supply amount on the given date. |
| `lowTotalSupplyAmount` | Low Total Supply Amount | float | The low total supply amount on the given date. |
| `closeTotalSupplyAmount` | Close Total Supply Amount | float | The close total supply amount on the given date. |
| `openAvailableLiquidityAmount` | Open Available Liquidity Amount | float | The open total available liquidity amount on the given date (amount that can be borrowed). |
| `highAvailableLiquidityAmount` | High Available Liquidity Amount | float | The high total supply amount on the given date (amount that can be borrowed). |
| `lowAvailableLiquidityAmount` | Low Available Liquidity Amount | float | The low total supply amount on the given date (amount that can be borrowed). |
| `closeAvailableLiquidityAmount` | Close Available Liquidity Amount | float | The close total supply amount on the given date (amount that can be borrowed). |
| `changesDetected` | Changes Detected | int32 | The number of changes detected for any of the above variables on blockchain during the resample window. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
|  | Pool Code | yes | string | The pool code associated with the yield pool. Must be a single pool code. |
| `startDate` | Start Date | no | datetime | If startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or later than the startDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | datetime | f startDate or endDate is not null, historical data will be queried. This filter limits metrics to on or less than the endDate (<=). Parameter must be in YYYY-MM-DD format. |
| `resampleFreq` | Resample Freq | no | string | This allows you to set the frequency in which you want data resampled. For example "1hour" would return the data where OHLC is calculated on an hourly schedule. The minimum value is "1min". Units in minutes (min), hours (hour), and days (day) are accepted. Format is # + (min/hour/dau); e.g. "15min", "4hour" or "1day". If no value is provided, defaults to 5min. |

**Examples**

**Response:**

```
[
    {
        "date":"2022-09-04T04:00:00.000Z",
        "openVariableBorrowRate":0.0125132918,
        "highVariableBorrowRate":0.0125132918,
        "lowVariableBorrowRate":0.0125132918,
        "closeVariableBorrowRate":0.0125132918,
        "openStableBorrowRate":0.0962566459,
        "highStableBorrowRate":0.0962566459,
        "lowStableBorrowRate":0.0962566459,
        "closeStableBorrowRate":0.0962566459,
        "openTotalStableBorrowAmount":5276178.2087150002,
        "highTotalStableBorrowAmount":5276179.0887550004,
        "lowTotalStableBorrowAmount":5276178.2087150002,
        "closeTotalStableBorrowAmount":5276179.0887550004,
        "openTotalVariableBorrowAmount":422688044.5946279764,
        "highTotalVariableBorrowAmount":422688053.6515100002,
        "lowTotalVariableBorrowAmount":422688044.5946279764,
        "closeTotalVariableBorrowAmount":422688053.6515100002,
        "openTotalBorrowAmount":427964084.341573,
        "highTotalBorrowAmount":427964093.2847998142,
        "lowTotalBorrowAmount":427964084.341573,
        "closeTotalBorrowAmount":427964093.2847998142,
        "openSupplyRate":0.0034360052,
        "highSupplyRate":0.0034360052,
        "lowSupplyRate":0.0034360052,
        "closeSupplyRate":0.0034360052,
        "openTotalSupplyAmount":1520033909.4199221134,
        "highTotalSupplyAmount":1520033918.3631489277,
        "lowTotalSupplyAmount":1520033909.4199221134,
        "closeTotalSupplyAmount":1520033918.3631489277,
        "openAvailableLiquidityAmount":1092069825.0783491135,
        "highAvailableLiquidityAmount":1092069825.0783491135,
        "lowAvailableLiquidityAmount":1092069825.0783491135,
        "closeAvailableLiquidityAmount":1092069825.0783491135,
        "changesDetected":11.0
    }
    ...
 ]
    
```
