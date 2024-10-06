module de.bmarwell.aktienfinder.scraper.app {
    requires info.picocli;
    requires de.bmarwell.aktienfinder.scraper.library;

    exports de.bmarwell.aktienfinder.scraper.app to
            info.picocli;

    opens de.bmarwell.aktienfinder.scraper.app to
            info.picocli;
}
