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
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndexStockRetriever;
import de.bmarwell.aktienfinder.scraper.library.scrape.DomHelper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class AbstractWallstreetOnlineDeScraper implements StockIndexStockRetriever {

    private static final Pattern ISIN_EXTRACTOR = Pattern.compile("&isin=([a-zA-Z0-9]+)&");

    public abstract URI uri();

    @Override
    public List<Stock> getStocks(Playwright blocking) {
        List<Stock> stocks = new ArrayList<>();

        try (Browser firefox = blocking.firefox().launch()) {
            BrowserContext context = firefox.newContext();

            Page page = firefox.newPage();
            Response navigation = page.navigate(uri().toString());
            navigation.finished();

            System.out.println("navigated to: " + uri() + " with status code = " + navigation.status());

            ElementHandle nextArrow = null;
            int pageNumber = 0;
            do {
                if (nextArrow != null) {
                    DomHelper.tryScrollIntoView(nextArrow);
                    System.out.println(nextArrow.asElement());
                    nextArrow.click();
                }

                pageNumber++;

                System.out.println("Reading from page #" + pageNumber);
                extractFromCurrentPage(page, stocks);

                nextArrow = page.querySelectorAll("div#highlowvalues a.next.page-link").stream()
                        .filter(ElementHandle::isVisible)
                        .filter(ElementHandle::isEnabled)
                        .findFirst()
                        .orElse(null);
            } while (nextArrow != null && nextArrow.asElement().isEnabled());
        }

        return List.copyOf(stocks);
    }

    private void extractFromCurrentPage(Page page, List<Stock> stocks) {
        ElementHandle mainTable = page.querySelector("table#highlowvalues");
        DomHelper.tryScrollIntoView(mainTable);

        mainTable.querySelector("tbody").waitForElementState(ElementState.VISIBLE);

        for (ElementHandle tbodyTr : mainTable.querySelectorAll("tbody tr")) {
            List<ElementHandle> stock = tbodyTr.querySelectorAll("td");
            String stockName = stock.get(0).innerText();

            System.out.println("found stock: " + stockName);

            // String chartUrl = stock.get(11).querySelector("img").getAttribute("src");
            // String query = URI.create(chartUrl).getQuery();
            // Matcher matcher = ISIN_EXTRACTOR.matcher(query);
            // boolean found = matcher.find();
            //
            // if (!found) {
            //    continue;
            // }
            //
            // String isin = matcher.group(1);
            //
            // stocks.add(new Stock(stockName, isin, Optional.of(getName())));
        }
    }
}
