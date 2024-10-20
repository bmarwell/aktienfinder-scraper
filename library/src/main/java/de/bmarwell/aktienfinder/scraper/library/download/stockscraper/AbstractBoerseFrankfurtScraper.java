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
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndexStockRetriever;
import de.bmarwell.aktienfinder.scraper.library.scrape.DomHelper;
import de.bmarwell.aktienfinder.scraper.value.Isin;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBoerseFrankfurtScraper implements StockIndexStockRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBoerseFrankfurtScraper.class);

    private static final Pattern ISIN_EXTRACTOR = Pattern.compile("&isin=([a-zA-Z0-9]+)&");

    public abstract URI uri();

    @Override
    public List<Stock> getStocks(Playwright blocking) {
        List<Stock> stocks = new ArrayList<>();

        try (Browser browser = blocking.chromium().launch()) {
            Page page = browser.newPage();
            Response navigation = page.navigate(uri().toString());
            navigation.finished();

            LOG.debug("navigated to: [{}]  with status code = [{}]", uri(), navigation.status());

            ElementHandle nextArrow = null;
            int pageNumber = 0;
            do {
                if (nextArrow != null) {
                    DomHelper.tryScrollIntoView(nextArrow);
                    nextArrow.click();
                }

                pageNumber++;

                LOG.debug("Reading from page #{}", pageNumber);
                extractFromCurrentPage(page, stocks);

                nextArrow = page.querySelector(
                        "app-page-bar div.page-bar-row div.ng-star-inserted button.page-bar-type-button.btn.btn-lg span.icon-arrow-step-right-grey-big");
            } while (nextArrow != null && nextArrow.asElement().isEnabled());
        }

        return List.copyOf(stocks);
    }

    private void extractFromCurrentPage(Page page, List<Stock> stocks) {
        Locator mainTableBody = page.locator("table tbody");
        ElementHandle mainTableBodyHandle = mainTableBody.elementHandle();
        DomHelper.tryScrollIntoView(mainTableBodyHandle);

        for (ElementHandle tbodyTr : mainTableBodyHandle.querySelectorAll("tbody tr")) {
            List<ElementHandle> stock = tbodyTr.querySelectorAll("td");
            String stockName = stock.getFirst().innerText();
            String chartUrl = stock.get(11).querySelector("img").getAttribute("src");
            String query = URI.create(chartUrl).getQuery();
            Matcher matcher = ISIN_EXTRACTOR.matcher(query);
            boolean found = matcher.find();

            if (!found) {
                continue;
            }

            String isin = matcher.group(1);

            stocks.add(new Stock(stockName, Isin.fromString(isin), Optional.of(getName())));
        }
    }
}
