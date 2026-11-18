# Real-time IEX Stock Market Data API

Source: https://www.tiingo.com/products/ (component `tiingo-iex`).

## Real-time IEX Stock Market Data API

### A High-Performance IEX API Optimized for Minimal Latency

### The Benefits of Tiingo's Real-time IEX Stock Market Data API

#### Access to Our Direct Cross-Connect

Tiingo has a direct cross-connect into the IEX exchange that we now make accessible to you. A cross-connect means we get access via a physical port in the IEX Exchange and pass that data directly to you - whether it's throw the raw firehose at the nanosecond resolution (Websockets and REST), or Historical OHLC bars (REST).

You can now experience live tick data coming in at the nanosecond resolution, all included in your Tiingo subscription.

#### Minimal Latency Throughout Our Entire Stack

We have built our entire stack to deliver market data with minimal latency. It starts with a machine directly at NY5 connected to the IEX Exchange, and then making sure we have a bare-metal cloud hosting servers less than 30 miles away from NY5. From this data center, we can scale out our operations. Additionally, having a bare metal stack with Packet.net means we get exceptional network stability and thoroughput. You can read about our benchmarks on why we distribute via Packet.net here: [AWS vs Packet.net](https://blog.tiingo.com/switched-away-aws-packet-net-benchmarking-networking-disk-processing-speeds/)

#### Enriched Data for Fuller Data

IEX is great and is transforming not just exchanges but also data licensing. They have an open license, allowing us to liberally share IEX exchange data to all. However, since it is newer to the block, it takes awhile to develop market share. IEX is about 4.5% of market share (NASDAQ is around 14% for reference). This means for large-medium cap companies and ETFs, the prices tick quickly, but for less liquid tickers, it can take some time to get an updated quote. To change that, we developed a proprietary algorithm to determine and enrich these quotes. Our algorithm determines when mid is approximately inline with the market and updates our API to reflect when this is the case (check out the "tngoLast" field). This means you can deliver to your users richer and fuller charts and also show them closer referential prices to market.

#### Speed and Uptime

We aim to have consistent uptime as well as some of the fastest servers possible with optimized in-memory caching, so we can deliver data quickly and consistently. Check out our independently-audited uptime here: [Tiingo Uptime](https://uptime.tiingo.com)

#### Quick-to-Action Support

We say quick-to-action, because anybody can respond to a ticket in 24 hours. But our team looks to solve issues immediately. Come say hello and check it out: E-mail us at Support@tiingo.com and see our response times.

#### Pricing Summary

Summary

Coverage

3,800-4000 tickers quoted daily

History

Intraday data from August 2017

Fields Available

Open, High, Low, Last, Best Bid, Best Ask, Best Bid Quantity, Best Ask Quantity

Update Frequency

Real-time - Prices Updated Instantly

Access Methods

REST for historical intraday, top-of-book, and last

Websockets for real-time tick trades and top-of-book updates

#### Product Summary

Plan

Free

Power

Commercial

Entity

Individual

Business

License

Internal Use Only

Usage Limits

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

80,000

1,200,000

1 TB

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
