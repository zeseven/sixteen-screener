# Financial News API Documentation

Source: https://www.tiingo.com/documentation/ (component `tiingo-news`).

**REST Endpoints**

```
# News Data Endpoint
https://api.tiingo.com/tiingo/news
```

Just remember, you will need your token in order to connect. Keep it safe.

.

### 2.2.1 Overview

Tiingo's news feeds are some of the most comprehensive news feeds available. We not only incorporate financial news sites, but also financial blogs - including those of small-time reputable bloggers, and tag them using the latest algos our team has been developing for over a decade. We bring these powerful feeds to you.

On a typical day, Tiingo adds over 8,000-12,000 articles a day.

You can find out about the full product offering on the Product - News page.

### 2.2.2 News Endpoint

**To request news data, use the following REST endpoints.**

```
# For the latest news
https://api.tiingo.com/tiingo/news

# For the latest news for specific tickers
https://api.tiingo.com/tiingo/news?tickers=aapl,googl

# For the latest news picked up by our crawler, you can sort by crawlDate
https://api.tiingo.com/tiingo/news?sortBy=crawlDate
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `id` | Article ID | int32 | Unique identifier specific to the news article. |
| `title` | Title | string | Title of the news article. |
| `url` | URL | string | URL of the news article. |
| `description` | Description | string | Long-form descripton of the news story. |
| `publishedDate` | Published Date | datetime | The datetime the news story was published in UTC. This is usually reported by the news source and not by Tiingo. If the news source does not declare a published date, Tiingo will use the time the news story was discovered by our crawler farm. |
| `crawlDate` | Crawl Date | datetime | The datetime the news story was added to our database in UTC. This is always recorded by Tiingo and the news source has no input on this date. If you note a large gap between crawlDate and publishedDate, it means the news article was backfilled, which usually happens when we add a new data source or gain access to historical data archives. |
| `source` | News Source | string | The domain the news source is from. |
| `tickers` | Tickers | string[] | What tickers are mentioned in the news story using Tiingo's proprietary tagging algo. |
| `tags` | Tags | string[] | Tags that are mapped and discovered by Tiingo using Tiingo's proprietary tagging algo. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `tickers` | Tickers | no | string[] | A comma-separated list of the tickers requested. When passing via GET parameter (not in URL), use a JSON array, otherwise use a comma-separated list. #Use the below format if querying directly in the URL https://api.tiingo.com/tiingo/news?tickers=aapl,googl #Otherwise, if passing GET parameters via a request library, use a JSON array like this: tickers : ['aapl','googl'] |
| `source` | Source (domains) | no | string[] | A comma-separated list of the tags requested. When passing via GET parameter (not in URL), use a JSON array, otherwise use a comma-separated list. #Use the below format if querying directly in the URL https://api.tiingo.com/tiingo/news?source=bloomberg.com,reuters.com #Otherwise, if passing GET parameters via a request library, use a JSON array like this: source : ['bloomberg.com','reuters.com'] |
| `startDate` | Start Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits news stories to on or later than the publishedDate (>=). Parameter must be in YYYY-MM-DD format. |
| `endDate` | End Date | no | date | If startDate or endDate is not null, historical data will be queried. This filter limits news stories to on or less than the publishedDate (<=). Parameter must be in YYYY-MM-DD format. |
| `limit` | Limit | no | int32 | The maximum number of news objects to return in the array. The default is 100 and the max is 1000. This is maxed to 1,000 as any more than that tends to lead to longer wait times. If you would like this adjusted, please contact us at support@tiingo.com. |
| `offset` | Offset | no | int32 | This is a "pagination" variable that is often used alongside limit. It returns an array with the results shifted by "offset". For example, when limit is 0 you may get the following: 0. News Story A 1. News Story B 2. News Story C 3. News Story D When offset is "2" you will receive: 0. News Story C 1. News Story D 2. News Story E 3. News Story F This, when used alongside "limit" allows for pagination and more efficient queries. |
| `sortBy` | Sort By | no | string | The field can take either "publishedDate" or "crawlDate". The date field specified will be used to sort the results in descending order. The results can either be sorted by when the news outlet reported the publish time ("datePublished") or when our feeds came across is ("crawlDate"). Defaults to "publishedDate" if no value provided |

**Examples**

**Response:**

```
[{
    "source":"cnbc.com",
    "crawlDate":"2019-01-29T22:20:01.696871Z",
    "description":"Apple CEO Tim Cook told CNBC that trade tensions between the U.S. and China have improved since late
    December.",
    "url":"https://www.cnbc.com/2019/01/29/apples-ceo-sees-optimism-as-trade-tension-between-us-and-china-lessens.html",
    "publishedDate":"2019-01-29T22:17:00Z",
    "tags":[
    "China",
    "Economic Measures",
    "Economics",
    "Markets",
    "Stock",
    "Technology",
    "Tiingo Top",
    "Trade"
    ],
    "tickers":[
    "aapl"
    ],
    "id":15063835,
    "title":"Apple CEO Tim Cook on US-China trade negotiations: 'There is a bit more optimism in the air'"
}...]
    
```

### 2.2.3 Bulk Download

To download the entire database, use the following REST endpoints. An "incremental" batchType file is added every evening. At 12am EST a batch process runs saving down all news articles for the past 24 hours.

Note: Bulk Download can only be opened up to our institutional clients.

**To download the entire database, use the following REST endpoints.**

```
# For a list of all the bulk download files
https://api.tiingo.com/tiingo/news/bulk_download

# To download a specific batch file
https://api.tiingo.com/tiingo/news/bulk_download/{id}
```

**Response**

| Field | Name | Type | Description |
|---|---|---|---|
| `id` | File ID | int32 | Unique identifier specific to the bulk download file. Used to select which file to download. |
| `url` | URL | string | A url you can use to directly download the batch file. NOTE: This url contains your Auth Token and is meant to be a "copy/paste" url for your convenience |
| `filename` | Filename | string | The filename of the batch file. |
| `batchType` | Batch Type | string | Describes what kind of batch file this is. The value will either be: "base" or "incremental". base is an entire dump of the data and incremental is a partial dump of the data. This should be used in conjunction with the startDate and endDate parameters to identify the bounds of the data dump. |
| `startDate` | Start Date | date | The start date that was used to select the News objects to generate the batch file. This is inclusive. publishedDate >= startDate. |
| `startDate` | End Date | date | The end date that was used to select the News objects to generate the batch file. This is not inclusive. publishedDate < endDate. |
| `fileSizeCompressed` | Compressed file size | int64 | The size of the file in bytes compressed using gzip. |
| `fileSizeUncompressed` | Uncompressed file size | int64 | The size of the file in bytes uncompressed. |

**Request**

| Field | Name | Required | Type | Description |
|---|---|---|---|---|
| `id` | File ID | yes | int32 | The unique "id" that corresponds to the batch file you would like to download. If none is supplied, returns a list of the available batch files. |

**Examples**

**Response:**

```
[
    {
        "id":755,
        "filename":"bulkfile_2019-01-28_2019-01-29.tar.gz",
        "batchType":"incremental",
        "startDate":"2019-01-28T05:00:00Z",
        "endDate":"2019-01-29T05:00:00Z",
        "url":"https://api.tiingo.com/tiingo/news/bulk_download/755?token=YOUR_API_TOKEN",
        "fileSizeUncompressed":10385878,
        "fileSizeCompressed":3018550
    },
    ...
]
```
