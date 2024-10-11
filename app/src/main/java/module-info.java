/**
 * The {@code de.bmarwell.aktienfinder.scraper.app} module provides the main application and command-line interface
 * for the Aktienfinder scraper. This module requires other modules and exports the application packages for use.
 * <p>Required modules:
 * <ul>
 * <li>{@code info.picocli} - for command-line interface parsing and functionality.</li>
 * <li>{@code de.bmarwell.aktienfinder.scraper.library} - for the core scraping library functionality.</li>
 * <li>{@code jakarta.json} - for JSON parsing and processing.</li>
 * </ul>
 * </p>
 * <p>Exported packages:
 * <ul>
 * <li>{@code de.bmarwell.aktienfinder.scraper.app} - for use by the {@code info.picocli} module.</li>
 * </ul>
 * </p>
 * <p>Opened packages:
 * <ul>
 * <li>{@code de.bmarwell.aktienfinder.scraper.app} - for deep reflection by the {@code info.picocli} module.</li>
 * </ul>
 * </p>
 */
module de.bmarwell.aktienfinder.scraper.app {
    requires info.picocli;
    requires de.bmarwell.aktienfinder.scraper.library;
    requires jakarta.json;

    exports de.bmarwell.aktienfinder.scraper.app to
            info.picocli;

    opens de.bmarwell.aktienfinder.scraper.app to
            info.picocli;
}
