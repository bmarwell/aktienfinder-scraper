module de.bmarwell.aktienfinder.scraper.app {
    requires info.picocli;
    requires de.bmarwell.aktienfinder.scraper.library;
    requires jakarta.json;

    exports de.bmarwell.aktienfinder.scraper.app to
            info.picocli;

    opens de.bmarwell.aktienfinder.scraper.app to
            info.picocli;
}
