# 24x5 Real-time Stock Data API: Consolidated US Equities

Source: https://www.tiingo.com/products/ (component `tiingo-consolidated-realtime-product`).

## 24x5 Consolidated Real-time Stock Data API

### Real-time US stock valuations and risk spreads, unencumbered by exchange licensing, from Sunday 8 PM to Friday 8 PM ET.

Last updated: November 4, 2026

Tiingo's 24x5 Consolidated Real-time Stock Data API is the simplest way we know of to add real-time US stock prices to an app - and for most individual developers and small teams, it's all you'll need. It delivers derived real-time reference prices, liquidity bid/ask metrics, and intraday OHLC bars for US stocks and ETFs from Sunday 8:00 PM ET through Friday 8:00 PM ET. Prices are derived from multiple US equity venues, including exchanges, alternative trading systems (ATSs), and OTC venues, and are delivered over both REST and WebSocket. Because the data is derived and its methodology is approved for redistribution by four or more trading venues, it is unencumbered by exchange licensing: your end users need no exchange licenses, and there are no per-user exchange fees or exchange usage reporting. The feed is included at no additional charge on every self-serve plan, including the free Starter plan ($0/month), Power ($30/month), and Commercial ($50/month). It is an alternative to Tiingo's IEX feed for applications that need real-time valuations and risk spreads rather than a pure raw execution venue feed, and it goes further than IEX Derived by drawing on multiple venues around the clock. IEX remains fully available, including the raw IEX execution venue feed for clients who need it.

REST DocumentationWebSocket DocumentationView Plans

### The Benefits of Tiingo's 24x5 Consolidated Real-time API

#### Simple to Build With

Building a trading terminal was actually our original vision for Tiingo back in 2014. We ended up spending so much of our time on data licensing and data quality that we made solving those our job instead - so you can spend your time on the app.

The 24x5 feed is where that work shows up the most. Each stock comes down to one consolidated price (`tngoLast`) and a liquidity spread (`lqSpread`), so there's no order book to rebuild or trade tape to reconcile. The same endpoints and WebSocket subscriptions run from Sunday 8:00 PM ET through Friday 8:00 PM ET, so overnight prices don't need a second integration. And because the data is derived, your users don't need exchange agreements, you don't have to classify anyone as professional or non-professional, and there are no usage reports to file.

#### Get a Live Price in One Request

Here's what one request looks like with a free API token:

```
curl "https://api.tiingo.com/tiingo/equity/intraday/?tickers=aapl,spy&token=YOUR_API_TOKEN"
```

You get back the consolidated price, previous close, and liquidity spread for each ticker (trimmed a bit here):

```
[
    {
        "ticker": "AAPL",
        "timestamp": "2019-01-30T10:33:38.186520297-05:00",
        "tngoLast": 162.33,
        "prevClose": 154.68,
        "prevDate": "2019-01-29",
        "lqSpread": 0.0011,
        "lqBidPrice": 162.30,
        "lqAskPrice": 162.36
    },
    ...
]
```

For streaming updates, connect to `wss://api.tiingo.com/equity/intraday` and subscribe with `thresholdLevel` 6 for reference prices or 4 for liquidity spreads. See the REST and WebSocket documentation for copy-and-paste examples in Python, Node.js, and PHP.

#### What Most Individuals and Small Teams Need

If you're an individual developer, a startup, or a small team, you most likely need a live price and a sense of how wide the market is - for portfolio values, watchlists, charts, alerts, dashboards, research, or an AI agent. That's what this feed gives you, and we estimate it covers about 98% of end-customer use cases.

If you need the raw exchange feeds, then yes, exchange licensing gets passed down to you. For almost everyone building apps, high-frequency execution data is overkill, and our derived valuations and spreads are more than enough. Start on the free Starter plan ($0/month), move to Power ($30/month) or Commercial ($50/month) as you grow, and only look at venue-native data if you end up needing executable quotes.

#### An Exchange-Unencumbered Dataset

Real-time stock data has traditionally come with strings attached. Venue-native quotes typically require exchange agreements, per-user fees that depend on whether each user is a professional or non-professional, and regular usage reporting that counts every person who sees the data. For a small team shipping an app, that overhead can cost more than the data itself.

The 24x5 feed removes those exchange obligations. Tiingo's reference prices and liquidity metrics are derived values, and the methodology has been approved by four or more trading venues for redistribution without requiring each downstream user to hold a separate exchange license. That means no per-user exchange fees, no subscriber counting, and no exchange usage reporting for your end users. Tiingo is the first company to make these exchange-compliant derived metrics available.

Self-serve plans are licensed for internal use. To show the data to people outside your organization, you need one Tiingo redistribution license rather than a set of separate exchange agreements.

#### Built for App, Blockchain, and AI Developers

Each security comes down to two numbers most applications need: a real-time valuation (the reference price) and a risk spread (the liquidity spread). That makes the feed a simple, lightweight building block:

- **App and fintech developers:** show live portfolio values, charts, watchlists, and alerts without building an exchange-compliance program for your users.
- **Blockchain developers:** exchange per-user licensing assumes you can count and classify every user, which a smart contract or oracle network cannot do. Derived reference prices fit oracle feeds and on-chain valuations, and the liquidity spread is a ready input for collateral haircuts and liquidation buffers.
- **AI and agent developers:** one REST call returns a compact JSON valuation and spread for a ticker, so agents and tools can price assets without parsing order books or trade tapes.
- **Portfolio valuation and risk teams:** mark positions continuously across the trading week and see how wide the market is for each holding, not just the last trade.

US equity markets close from Friday 8:00 PM ET to Sunday 8:00 PM ET, so applications that run on weekends, such as on-chain markets, should plan for prices to hold at their Friday values until Sunday evening.

#### 24x5 Coverage, Not Just Market Hours

The feed covers two sessions that together run 24 hours a day, five days a week:

- **Daytime and extended hours:** 4:00 AM to 8:00 PM ET, Monday through Friday.
- **Overnight:** 8:00 PM to 3:59 AM ET, Sunday through Thursday nights.

Combined, that is continuous coverage from Sunday 8:00 PM ET through Friday 8:00 PM ET. The same endpoints, fields, and WebSocket subscriptions work across both sessions, so you don't need a second integration for overnight prices.

#### Derived From Multiple Venues

Tiingo consolidates market data from multiple US equity venues (exchanges, ATSs, and OTC venues) and calculates a single reference price for each security. The reference price (`tngoLast`) is calculated from validated consolidated quote mids or trade prints, and updates as often as every 250 milliseconds when data is available. Because it draws on many venues instead of one, the reference price keeps updating for less liquid securities that trade infrequently on any single exchange.

#### Liquidity Risk Metrics

Alongside the reference price, the feed publishes a liquidity spread (`lqSpread`) and an estimated liquidity bid/ask profile (`lqBidPrice`, `lqAskPrice`, `lqBidSize`, `lqAskSize`). These are statistical valuation and risk metrics built from spread and liquidity observations across venues. They are designed for portfolio valuation, risk, and display, and they are not executable quotes.

#### Historical Intraday Bars

The same pipeline builds historical intraday OHLC bars. Resample from 1 minute up to multi-hour bars, include after-hours data, and force-fill empty intervals for charting. Daytime one-minute history reaches back to approximately 2017. Overnight history has been collected since the overnight session launched in July 2026 and is not retroactive. Historical bars are unadjusted; apply our dividend and split data if you need adjusted bars.

#### Speed and Uptime

We aim to have consistent uptime as well as some of the fastest servers possible with optimized in-memory caching, so we can deliver data quickly and consistently. Check out our independently-audited uptime here: [Tiingo Uptime](https://uptime.tiingo.com)

#### Quick-to-Action Support

We say quick-to-action, because anybody can respond to a ticket in 24 hours. But our team looks to solve issues immediately. Come say hello and check it out: E-mail us at Support@tiingo.com and see our response times.

#### Product Summary

Summary

Coverage

US stocks and ETFs

Hours

Sunday 8:00 PM ET through Friday 8:00 PM ET

4:00 AM - 8:00 PM ET daytime and extended hours

8:00 PM - 3:59 AM ET overnight, Sunday through Thursday

Sources

Derived from multiple US exchanges, ATSs, and OTC venues

Fields Available

Reference price (tngoLast), liquidity spread, liquidity bid/ask prices and sizes, previous close, day open/high/low, intraday OHLC bars

Update Frequency

Real-time, as often as every 250 ms when data is available

History

Daytime intraday bars from approximately 2017; overnight bars from July 2026

Access Methods

REST for snapshots and historical intraday bars

WebSocket firehose for reference price and liquidity metric updates

### 24x5 Consolidated vs IEX vs BOATS vs SIP

In US market data, "consolidated" usually means the SIP feeds. Tiingo's 24x5 feed is a derived consolidated feed instead. Here is how it compares with the other real-time US equity feeds Tiingo offers, and with SIP data.

|  | Tiingo 24x5 Consolidated | Tiingo IEX | Tiingo BOATS | SIP (CTA/UTP) |
|---|---|---|---|---|
| Hours | Sunday 8:00 PM ET through Friday 8:00 PM ET | 8:00 AM - 5:00 PM ET, Monday through Friday | 8:00 PM - 3:59 AM ET, Sunday through Thursday nights | Exchange session hours, generally 4:00 AM - 8:00 PM ET |
| Venues | Multiple US exchanges, ATSs, and OTC venues | IEX only | Blue Ocean ATS only | All US national securities exchanges, plus off-exchange trades reported to FINRA |
| Data type | Derived reference prices and liquidity metrics | Venue-native IEX quotes and trades, plus Tiingo reference price | Venue-native BOATS quotes and trades | Official consolidated quotes (NBBO) and trades |
| Fields | Reference price, liquidity bid/ask/spread, day OHLC, intraday bars | IEX bid/ask and sizes, last sale, intraday bars from August 2017 | Bid/ask/mid, last trade price and size, overnight bars | NBBO, every trade print, consolidated volume |
| Licensing | Exchange-unencumbered: no exchange licenses, per-user exchange fees, or exchange reporting for your users. Self-serve plans are internal use; external display needs one Tiingo redistribution license | Self-serve plans are internal use; external display through a Tiingo redistribution license | Internal use with the BOATS add-on; display or non-display use requires a BOATS license | Exchange agreements, typically with per-user fees and usage reporting |
| Price | Included on every self-serve plan, including free Starter | Included on every self-serve plan, including free Starter | +$9/month on Power or Commercial; not available on Starter | Set by exchange fee schedules; not offered by Tiingo |
| Best for | What most individual developers and small teams need: real-time valuations and risk spreads for apps, charts, alerts, portfolio valuation, blockchain, and AI tools | Existing IEX integrations and IEX-specific fields | Executable overnight quotes and overnight order routing | Best-execution, regulatory NBBO, and full consolidated volume |

#### Pricing Summary

Plan

Starter (Free)

Power

Commercial

Entity

Individual

Business

License

Internal Use Only

24x5 Consolidated

Included

Usage Limits

Unique Symbols

500/month

All

Hourly Requests

50

10,000

20,000

Daily Requests

1,000

100,000

150,000

Monthly Bandwidth

1 GB

40 GB

100 GB

Plan Price

$0/month

$30/month

$50/month

Base Equities Redistribution

Business

Display Redistribution

24x5 Consolidated Real-time

No per-user exchange fees or reporting

Includes:

- End-of-Day Prices
- 24x5 Real-time
- Corporate Actions
- Security Master
- Real-time IEX Derived
- All data runs through our [proprietary cleansing methodology](https://www.tiingo.com/blog/tiingo-data-quality-how-we-find-and-fix-market-data-errors/)

Formerly EOD + IEX Redistribution - now expanded with 24x5 Real-time, Corporate Actions, and Security Master

$250/Month for Start-ups

$500/Month for Enterprise

Contact sales to get started

### Frequently Asked Questions

#### What is the simplest real-time stock price API for developers?

We built Tiingo's 24x5 Consolidated Real-time API to be the simplest one we could. One REST request with a free API token gives you a consolidated price and liquidity spread for each stock in plain JSON, and the same endpoints and WebSocket subscriptions work from Sunday 8:00 PM ET through Friday 8:00 PM ET. You don't rebuild an order book or wire up a separate overnight feed, and your users don't need exchange agreements or usage reporting.

#### Which real-time stock API should an individual developer or small team use?

For most individuals and small teams, we'd recommend Tiingo's 24x5 Consolidated Real-time API - it's what most of our startup clients choose. It covers apps, dashboards, portfolio trackers, alerts, research, and AI agents with one integration for the whole trading week, and it's included free on the Starter plan. If you need executable quotes for trading, order routing, or best-execution analysis, that's when venue-native data like IEX or BOATS makes sense.

#### Is Tiingo's 24x5 Consolidated Real-time API free?

Yes. Tiingo's 24x5 Consolidated Real-time API is included at no additional charge on every self-serve plan, including the free Starter plan. Starter accounts get 50 requests per hour, 1,000 requests per day, 1 GB of monthly bandwidth, and 500 unique symbols per month. Power ($30/month) and Commercial ($50/month) raise those limits and cover all symbols. WebSocket firehose access is included on all three plans.

#### What does "exchange-unencumbered" mean?

Venue-native real-time data usually comes with exchange obligations: agreements with each exchange, per-user fees based on professional or non-professional status, and regular reports counting who sees the data. Tiingo's 24x5 feed is derived, and its methodology is approved for redistribution by four or more trading venues, so none of those exchange obligations pass down to your users. Tiingo's own license terms still apply: self-serve plans are for internal use, and external display needs a Tiingo redistribution license.

#### Can I use Tiingo's 24x5 feed in a smart contract, oracle, or on-chain app?

Yes, with the right license. Prices published on-chain are visible to anyone, so on-chain use counts as redistribution and needs a Tiingo redistribution or white-label license. Because the feed carries no per-user exchange licensing, it avoids the problem of counting and classifying anonymous on-chain users. E-mail sales@tiingo.com with your use case. Note that US equity markets close from Friday 8:00 PM ET to Sunday 8:00 PM ET, so prices do not update over the weekend.

#### Is Tiingo's 24x5 feed SIP data?

No. SIP data is the official consolidated tape published by the Securities Information Processors (CTA for NYSE-listed securities and UTP for Nasdaq-listed securities). Tiingo's 24x5 feed is derived: Tiingo collects data from multiple exchanges, ATSs, and OTC venues and calculates its own reference price and liquidity metrics. The liquidity bid/ask values are statistical estimates, not the NBBO and not executable quotes. The `volume` field is a convenience field and is not full SIP consolidated volume.

#### Can I display Tiingo's real-time stock prices in my app or website?

Yes. Self-serve plans are licensed for internal use only, so showing the data to people outside your organization requires a Tiingo redistribution license. Because the feed is exchange-unencumbered, you don't need separate exchange agreements on top of it: your users don't need their own exchange licenses, and there are no per-user exchange fees or usage reporting. Our Base Equities redistribution license is $250/month for startups and $500/month for enterprise, and it covers End-of-Day Prices, 24x5 Real-time, Corporate Actions, Security Master, and Real-time IEX Derived - e-mail sales@tiingo.com to get started. If each of your users brings their own Tiingo API token, see the Developer Program.

#### How is Tiingo's 24x5 feed different from BOATS?

BOATS is venue-native data from a single overnight venue, Blue Ocean ATS, during the 8:00 PM - 3:59 AM ET session. It provides executable top-of-book quotes and trades, and it requires a separate +$9/month add-on. The 24x5 feed already includes derived overnight coverage, which covers most overnight charting, alerting, and app use cases without a BOATS entitlement. Choose BOATS when you need executable overnight quotes.

#### How is Tiingo's 24x5 feed different from IEX? Is it replacing IEX?

No, the 24x5 feed is not replacing IEX. It is an alternative for applications that need real-time valuations and risk spreads but don't need a pure raw execution venue feed. Tiingo's IEX feed comes in two forms, and both remain available:

- **IEX Derived:** a Tiingo reference price calculated from a single exchange's data during IEX hours (8:00 AM - 5:00 PM ET), included on every plan. The 24x5 feed is a step up from IEX Derived: it draws on multiple exchanges, ATSs, and OTC venues, covers Sunday 8:00 PM through Friday 8:00 PM ET, and adds liquidity spread and bid/ask risk metrics.
- **IEX venue-native:** the actual IEX quotes and trades, for workflows that need a raw execution venue feed, such as trading, order routing, or best-execution analysis. This requires an IEX market data agreement; e-mail sales@tiingo.com to get set up.

You can use the 24x5 feed and IEX side by side, and existing IEX integrations don't need to change.

#### How far back does Tiingo's 24x5 historical intraday data go?

Daytime one-minute bars reach back to approximately 2017. Overnight bars are available from July 2026, when overnight coverage launched. Each request returns up to 10,000 bars, so page through longer windows with bounded date ranges.

#### How fast does Tiingo's 24x5 feed update?

Reference prices update as often as every 250 milliseconds when data is available. The WebSocket firehose is tick-level and publishes whenever Tiingo detects a meaningful change in the consolidated reference price.
