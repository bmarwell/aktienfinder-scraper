/*
 * Copyright (C) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.bmarwell.aktienfinder.scraper.library.download.stockscraper;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Page.WaitForResponseOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndexStockRetriever;
import de.bmarwell.aktienfinder.scraper.library.scrape.DomHelper;
import java.net.URI;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for paginating web scrapers that retrieve stock data.
 * This class provides functionality to navigate through paginated content
 * and capture stocks from each page.
 *
 * <p>This class implements the {@link StockIndexStockRetriever} interface,
 * meaning concrete implementations must define how to retrieve stock data
 * for a specific stock index.
 *
 * <p>The main responsibilities of this class include:
 * <ul>
 *     <li>Navigating through paginated web content.
 *     <li>Capturing stock data from each page.
 * </ul>
 */
public abstract class AbstractPaginatableScraper implements StockIndexStockRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractPaginatableScraper.class);

    public abstract URI uri();

    abstract Pattern getPaginationEventPattern();

    boolean redirectDebug() {
        return false;
    }

    boolean tryAdvancePage(ElementHandle nextArrow, Page page, int pageNumber) {
        Consumer<Response> consumer = (r) -> {
            if (!r.url().contains(uri().getHost())) {
                return;
            }
            LOG.info("Page #{} of [{}], url = [{}]", pageNumber, getName(), r.url());
        };

        if (redirectDebug()) {
            page.onResponse(consumer);
        }

        int tries = 0;
        boolean advanced = false;

        while (!advanced && tries < 3) {
            LOG.trace("Try #{} to advance to page #{} of [{}].", tries, pageNumber, getName());
            tries++;
            advanced = doTryAdvance(nextArrow, page, pageNumber);
        }

        if (redirectDebug()) {
            page.offResponse(consumer);
        }

        return advanced;
    }

    private boolean doTryAdvance(ElementHandle nextArrow, Page page, int pageNumber) {
        try {
            DomHelper.tryScrollIntoView(nextArrow);

            WaitForResponseOptions opts = new WaitForResponseOptions();
            opts.setTimeout(20_000L);
            page.waitForResponse(getPaginationEventPattern(), opts, () -> nextArrow.click());

            return true;
        } catch (PlaywrightException pe) {
            LOG.error("Problem advancing page to #{} for stock [{}]", pageNumber, getName(), pe);
            ScreenshotOptions opts = new ScreenshotOptions();
            opts.setPath(Paths.get("/tmp/" + getName() + "_" + pageNumber + ".png"));
            page.screenshot(opts);
            LOG.error("Screenshot saved: [{}]", opts.path);

            return false;
        }
    }
}
