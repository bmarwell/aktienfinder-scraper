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

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.ElementState;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndexStockRetriever;
import de.bmarwell.aktienfinder.scraper.library.scrape.DomHelper;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base class for scraping stock index data from ComDirect.
 * This class extends {@link AbstractPaginatableScraper} and implements {@link StockIndexStockRetriever},
 * providing the core functionality to scrape and retrieve stock data from paginated web pages.
 *
 * <p>This scraper navigates through the ComDirect website, accepting cookies,
 * extracting stock data from current pages, and handling pagination to ensure
 * all available stock data is retrieved.
 *
 * <p>The class relies on composable parts such as {@link Browser}, {@link Page}, {@link ElementHandle},
 * and {@link Pattern} to perform these tasks.
 *
 * <p>Concrete implementations must define the stock index URI by overriding the {@code uri()} method
 * and specify the name of the stock index by implementing the {@code getName()} method.
 */
public abstract class AbstractComDirectScraper extends AbstractPaginatableScraper implements StockIndexStockRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractComDirectScraper.class);

    private static final Pattern ISIN_EXTRACTOR = Pattern.compile("/inf/aktien/([a-zA-Z0-9]+)$");

    private static final Pattern PAGINATION_EVENT_PATTERN =
            Pattern.compile(".*/inf/indizes/detail/werte/standard.html.*");

    @Override
    Pattern getPaginationEventPattern() {
        return PAGINATION_EVENT_PATTERN;
    }

    @Override
    public List<Stock> getStocks(Playwright blocking) {
        List<Stock> stocks = new ArrayList<>();

        try (Browser browser = blocking.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            Response navigation = page.navigate(uri().toString());
            navigation.finished();

            LOG.debug("navigated to: [{}]  with status code = [{}]", uri(), navigation.status());

            acceptCookies(page);

            ElementHandle nextArrow = null;
            int pageNumber = 0;

            do {
                pageNumber++;

                if (nextArrow != null) {
                    boolean pageAdvanced = tryAdvancePage(nextArrow, page, pageNumber);
                    if (!pageAdvanced) {
                        break;
                    }
                }

                acceptCookies(page);

                LOG.debug("Reading from page #{} of Index [{}]", pageNumber, getName());
                extractFromCurrentPage(page, stocks);

                nextArrow = page.locator("div.pagination__button.pagination__button--right a").elementHandles().stream()
                        .filter(ElementHandle::isVisible)
                        .filter(ElementHandle::isEnabled)
                        .findFirst()
                        .orElse(null);
            } while (nextArrow != null && nextArrow.asElement().isEnabled());
        }

        return List.copyOf(stocks);
    }

    private void acceptCookies(Page page) {
        var privacyAcceptButton = page.querySelector("button#privacy-init-wall-button-accept");
        if (privacyAcceptButton == null) {
            return;
        }

        if (privacyAcceptButton.isVisible() && privacyAcceptButton.isEnabled()) {
            DomHelper.tryScrollIntoView(privacyAcceptButton);
            privacyAcceptButton.click();
        }
    }

    private void extractFromCurrentPage(Page page, List<Stock> stocks) {
        ElementHandle mainTable = page.querySelector("table.table--comparison");
        DomHelper.tryScrollIntoView(mainTable);
        mainTable.querySelector("tbody").waitForElementState(ElementState.VISIBLE);

        for (ElementHandle tbodyTr : mainTable.querySelectorAll("tbody tr")) {
            ElementHandle stockNameCol = tbodyTr.querySelector("td[data-label=\"Name\"] a");

            if (stockNameCol == null) {
                continue;
            }

            String href = stockNameCol.getAttribute("href");

            if (href == null) {
                continue;
            }

            var matcher = ISIN_EXTRACTOR.matcher(href);
            if (!matcher.find()) {
                continue;
            }

            var isin = matcher.group(1);

            Stock stock = new Stock(stockNameCol.innerText(), isin, Optional.of(getName()));
            stocks.add(stock);
        }
    }
}
