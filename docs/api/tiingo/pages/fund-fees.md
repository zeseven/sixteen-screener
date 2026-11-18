# 2.9 REST - Mutual Fund & ETF Fees

Source: https://www.tiingo.com/documentation/ (component `tiingo-fund-fees`).

## Mutual Fund and ETF Fee Data API Documentation

**REST Endpoints**

This endpoint is for enterprise and institutional clients only. Please reach out to Sales@tiingo.com for licensing and pricing.

```
# To obtain top-level fund data, including description and share classes, use the following REST endpoint
https://api.tiingo.com/tiingo/funds/<ticker>

# To obtain detailed current and historical fee data, use the following REST endpoint
https://api.tiingo.com/tiingo/funds/<ticker>/metrics
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.9.1 Overview

Tiingo tracks and processes official Mutual Fund & ETF Fee data from over 36,000 Mutual Funds and ETFs. The data includes a detailed breakdown data of fees, even including custom fees (like check processing fee if withdrawing money via check).

Benefits of Mutual Fund & ETF Fee Data

- 36,000+ Mutual Funds and ETFs covered.
- Data includes historical as well as current data.
- Fee data is detailed and broken down (e.g. management fee, 12b-1 fees, and more).
- Fee data is updated intraday as new data comes online from Fund Companies.
- Multiple share classes are mapped allowing you to easily compare fees across share classes.

### 2.9.2 Fund Overview

**To obtain top-level fund data, including description and share classes, use the following REST endpoint.**

```
# To obtain top-level fund data, including description and share classes, use the following REST endpoint
https://api.tiingo.com/tiingo/funds/<ticker>
```

**Response**

To see what fields are returned in the "otherShareClasses" field, please see the table below.

**Examples**

**Response:**

```
{
    "ticker":"vfinx"
    "name":"VANGUARD 500 INDEX FUND INVESTOR SHARES",
    "shareClass":"INVESTOR SHARES",
    "description":"The Fund employs an indexing investment approach designed to track the performance of the Standard & Poor's 500 Index, a widely recognized benchmark of U.S. stock market performance that is dominated by the stocks of large U.S. companies. The Fund attempts to replicate the target index by investing all, or substantially all, of its assets in the stocks that make up the Index, holding each stock in approximately the same proportion as its weighting in the Index.",
    "netExpense":0.0014,
    "otherShareClasses":[
    {
        "ticker":"VFIAX",
        "name":"VANGUARD 500 INDEX FUND ADMIRAL SHARES",
        "shareClass":"ADMIRAL SHARES",
        "netExpense":0.0004
    },
    {
        "ticker":"VFFSX",
        "name":"VANGUARD 500 INDEX FUND INSTITUTIONAL SELECT SHARES",
        "shareClass":"INSTITUTIONAL SELECT SHARES",
        "netExpense":0.0001
    },
    {
        "ticker":"VOO",
        "name":"Vanguard S&P 500 ETF",
        "shareClass":"ETF SHARES",
        "netExpense":0.0003
    }
]
    
```

### 2.9.3 Historical and Current Mutual Fund & ETF Fee Data

**To obtain detailed current and historical fee data, use the following REST endpoint.**

```
# To obtain detailed current and historical fee data, use the following REST endpoint
https://api.tiingo.com/tiingo/funds/<ticker>/metrics
```

**Response**

To see what fields are returned in the "customFees" field, please see the table below.

**Examples**

**Response:**

```
[
    {
        "prospectusDate":"2021-03-01",
        "netExpense":0.0049,
        "grossExpense":0.0061,
        "managementFee":0.004,
        "12b1":0.0,
        "non12b1":0.0,
        "otherExpenses":0.0021,
        "acquiredFundFees":0.0,
        "feeWaiver":-0.0012,
        "exchangeFeeUSD":0.0,
        "exchangeFeePercent":0.0,
        "backLoad":0.0,
        "frontLoad":0.0,
        "dividendLoad":0.0,
        "shareholderFee":15.0,
        "accountFeeUSD":0.0,
        "accountFeePercent":0.0,
        "redemptionFeeUSD":0.0,
        "redemptionFeePercent":0.0,
        "portfolioTurnover":0.63,
        "miscFees":45.0,
        "customFees":[
            {
                "label":"Wire Fee",
                "value":20.0,
                "units":"$",
                "parentFee":"miscFees"
            },
            {
                "label":"Overnight check delivery fee",
                "value":25.0,
                "units":"$",
                "parentFee":"miscFees"
            }
        ],

    }
    ...
]
```
