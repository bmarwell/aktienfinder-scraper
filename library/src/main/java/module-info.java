module de.bmarwell.aktienfinder.scraper.library {
    requires org.slf4j;
    requires jakarta.json;
    requires playwright;
    requires org.apache.poi.ooxml;
    requires org.jspecify;

    exports de.bmarwell.aktienfinder.scraper.library;
    exports de.bmarwell.aktienfinder.scraper.library.scrape;
    exports de.bmarwell.aktienfinder.scraper.library.scrape.value;
    exports de.bmarwell.aktienfinder.scraper.library.download;
    exports de.bmarwell.aktienfinder.scraper.library.export;
    exports de.bmarwell.aktienfinder.scraper.library.download.stockscraper;
}
