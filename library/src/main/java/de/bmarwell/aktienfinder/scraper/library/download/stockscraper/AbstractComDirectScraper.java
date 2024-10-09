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
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.ElementState;
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndexStockRetriever;
import de.bmarwell.aktienfinder.scraper.library.scrape.DomHelper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractComDirectScraper implements StockIndexStockRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractComDirectScraper.class);

    private static final Pattern ISIN_EXTRACTOR = Pattern.compile("/inf/aktien/([a-zA-Z0-9]+)$");

    public abstract URI uri();

    @Override
    public List<Stock> getStocks(Playwright blocking) {
        List<Stock> stocks = new ArrayList<>();

        try (Browser browser = blocking.chromium().launch()) {
            Page page = browser.newPage();
            Response navigation = page.navigate(uri().toString());
            navigation.finished();

            LOG.debug("navigated to: [{}]  with status code = [{}]", uri(), navigation.status());

            acceptCookies(page);

            ElementHandle nextArrow = null;
            int pageNumber = 0;
            do {
                if (nextArrow != null) {
                    DomHelper.tryScrollIntoView(nextArrow);
                    nextArrow.click();
                }

                acceptCookies(page);

                pageNumber++;

                LOG.debug("Reading from page #{}", pageNumber);
                extractFromCurrentPage(page, stocks);

                nextArrow = page.querySelector("div.pagination__button.pagination__button--right a");
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
