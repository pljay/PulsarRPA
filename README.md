Pulsar README
===================

Pulsar is an open source solution to scrape web data at scale.

Extracting web data at scale is extremely hard. Websites change frequently and are becoming more complex, meaning web data collected is often inaccurate or incomplete, pulsar is an open source solution to address such issues.

Pulsar supports the Network As A Database paradigm, so we can turn the Web into tables and charts using simple SQLs, and we can query the web using SQL directly.

We also have a plan to release an advanced AI to automatically extract every field in webpages with notable accuracy.

![product-screenshot](docs/images/pulsar-product-screenshot-1.png)

# Features
- Web spider: browser rendering, ajax data crawling
- Performance: highly optimized, rendering hundreds of pages in parallel on a single machine without be blocked
- Data quantity assurance: smart retry, accurate scheduling, web data lifetime management
- Large scale: fully distributed, designed for large scale crawling
- Simple API: single line of code to scrape, or single SQL to turn a website into a table
- X-SQL: extend SQL to manage web data: Web crawling, scraping, Web content mining, Web BI
- Bot stealth: IP rotation, web driver stealth, never get banned
- RPA: imitating human behavior, SPA crawling, or do something else awesome
- Big data: various backend storage support: MongoDB/HBase/Gora
- Logs & metrics: monitored closely and every event is recorded

For more information check [platon.ai](http://platon.ai)
# Use pulsar as a library
Maven:
```
<dependency>
  <groupId>ai.platon.pulsar</groupId>
  <artifactId>pulsar-all</artifactId>
  <version>1.9.0</version>
</dependency>
```
## Basic usage:
### Kotlin
```kotlin
val url = "https://list.jd.com/list.html?cat=652,12345,12349"
val session = PulsarContexts.createSession()
// load a page, fetch it from the website if expired or doesn't exist
val page = session.load(url, "-expires 1d")
// parse the page into a Jsoup document
val document = session.parse(page)
// do something with the document
// ...
// load all pages with link specified by -outLink
val pages = session.loadOutPages(url, "-expires 1d -itemExpires 7d -outLink a[href~=/dp/]")
// load or fetch, parse and scrape fields
val fields = session.scrape(url, "-expires 1d", "li[data-sku]", listOf(".p-name em", ".p-price"))
// load or fetch, parse and scrape fields
val fields2 = session.scrape(url, "-i 1d", "li[data-sku]",
    mapOf("name" to ".p-name em", "price" to ".p-price"))
```
## Scrape a massive url collection:
### Kotlin
```kotlin
import ai.platon.pulsar.common.LinkExtractors
import ai.platon.pulsar.context.PulsarContexts
import ai.platon.pulsar.crawl.common.url.ParsableHyperlink
import ai.platon.pulsar.persist.WebPage
import org.jsoup.nodes.Document

fun main() {
    val parseHandler = { _: WebPage, document: Document ->
        // do something wonderful with the document
        println(document.title() + "\t|\t" + document.baseUri())
    }
    val urls = LinkExtractors.fromResource("seeds.txt")
        .map { ParsableHyperlink("$it -refresh", parseHandler) }
    val context = PulsarContexts.create().asyncLoadAll(urls)
    // feel free to fetch/load a huge number of urls here using async loading
    // ...
    context.await()
}
```
### Java
```java
public class MassiveCrawler {

    private static void onParse(WebPage page, Document document) {
        // do something wonderful with the document
        System.out.println(document.title() + "\t|\t" + document.baseUri());
    }

    public static void main(String[] args) {
        List<Hyperlink> urls = LinkExtractors.fromResource("seeds.txt")
                .stream()
                .map(seed -> new ParsableHyperlink(seed, MassiveCrawler::onParse))
                .collect(Collectors.toList());
        PulsarContext context = PulsarContexts.create().asyncLoadAll(urls);
        // feel free to fetch/load a huge number of urls here using async loading
        // ...
        context.await();
    }
}
```
## Use X-SQL to query the web

Scrape a single page:

```sql
select
    dom_first_text(dom, '#productTitle') as `title`,
    dom_first_text(dom, '#price tr td:contains(List Price) ~ td') as `listprice`,
    dom_first_text(dom, '#price tr td:matches(^Price) ~ td, #price_inside_buybox') as `price`,
    array_join_to_string(dom_all_texts(dom, '#wayfinding-breadcrumbs_container ul li a'), '|') as `categories`,
    dom_base_uri(dom) as `baseUri`
from
    load_and_select('https://www.amazon.com/dp/B00BTX5926', ':root')
```

Execute the X-SQL:

```kotlin
val context = SQLContexts.create()
context.executeQuery(sql)
```

The result is as follows:

    TITLE                                                            | LISTPRICE | PRICE  | CATEGORIES                                    | BASEURI
    Tara Toys Ariel Necklace Activity Set - Amazon Exclusive (51394) | $19.99    | $12.99 | Toys & Games|Arts & Crafts|Craft Kits|Jewelry | https://www.amazon.com/dp/B00BTX5926

# Use pulsar as a server

## Requirements

- Memory 4G+
- Maven 3.2+
- The latest version of the Java 11 OpenJDK
- java and jar on the PATH
- Google Chrome 90+

## Build from source

    git clone https://github.com/platonai/pulsar.git
    cd pulsar && bin/build.sh

## Start the pulsar server if not started

```shell
bin/pulsar
```

## Scrape a webpage in another terminal window

```shell
bin/scrape.sh
```

The response is as follows:

```json
{
    "uuid": "cc611841-1f2b-4b6b-bcdd-ce822d97a2ad",
    "statusCode": 200,
    "pageStatusCode": 200,
    "pageContentBytes": 1607636,
    "resultSet": [
        {
            "title": "Tara Toys Ariel Necklace Activity Set - Amazon Exclusive (51394)",
            "listprice": "$19.99",
            "price": "$12.99",
            "categories": "Toys & Games|Arts & Crafts|Craft Kits|Jewelry",
            "baseuri": "https://www.amazon.com/dp/B00BTX5926"
        }
    ],
    "pageStatus": "OK",
    "status": "OK"
}
```
# Advanced topics:
- How to scrape a single page application (SPA)
- How to download resources without browser rendering
- How to scrape page by page
- How to simulate human behavior
- How to scrape amazon.com to match industrial needs
