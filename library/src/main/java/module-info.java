module de.bmarwell.aktienfinder.scraper.library {
    requires java.net.http;
    requires org.slf4j;
    requires jakarta.json;

    exports de.bmarwell.aktienfinder.scraper.library;
    exports de.bmarwell.aktienfinder.scraper.library.scrape;
    exports de.bmarwell.aktienfinder.scraper.library.scrape.value;
    exports de.bmarwell.aktienfinder.scraper.library.download;
}
