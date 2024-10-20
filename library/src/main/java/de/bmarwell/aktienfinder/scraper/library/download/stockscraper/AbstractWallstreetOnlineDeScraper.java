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
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndexStockRetriever;
import de.bmarwell.aktienfinder.scraper.library.scrape.DomHelper;
import de.bmarwell.aktienfinder.scraper.value.Isin;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWallstreetOnlineDeScraper extends AbstractPaginatableScraper
        implements StockIndexStockRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractWallstreetOnlineDeScraper.class);

    public abstract URI uri();

    private boolean cookiesAccepted = false;

    private static final Pattern PAGINATION_EVENT_PATTERN =
            Pattern.compile(".*/_rpc/json/instrument/list/filter.*view=enthaltenewerte.*showAll=true");

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
                extractFromCurrentPage(page, stocks, context, pageNumber);

                nextArrow = page.locator("div#highlowvalues a.next.page-link").elementHandles().stream()
                        .filter(ElementHandle::isVisible)
                        .filter(ElementHandle::isEnabled)
                        .findFirst()
                        .orElse(null);
            } while (nextArrow != null && nextArrow.asElement().isEnabled());
        }

        return List.copyOf(stocks);
    }

    private void acceptCookies(Page page) {
        if (cookiesAccepted) {
            return;
        }

        for (Frame frame : page.frames()) {
            if (frame.isDetached()) {
                continue;
            }

            try {
                FrameLocator iframe = frame.frameLocator("iframe[title=\"SP Consent Message\"]");
                LOG.trace("iframe: {}", iframe);
                Locator akzeptierenUndWeiter = iframe.getByLabel("Akzeptieren und weiter");
                if (akzeptierenUndWeiter == null) {
                    continue;
                }

                if (akzeptierenUndWeiter.isEnabled() && akzeptierenUndWeiter.isVisible()) {
                    LOG.debug("click to [{}]", akzeptierenUndWeiter);
                    LOG.debug(
                            "click to [{}]",
                            akzeptierenUndWeiter.elementHandle().innerHTML());

                    akzeptierenUndWeiter.click();

                    cookiesAccepted = true;
                    break;
                }
            } catch (PlaywrightException pe) {
                LOG.info("iframe error [{}]", frame, pe);
            }
        }

        // dont try again
        cookiesAccepted = true;
    }

    private void extractFromCurrentPage(Page page, List<Stock> stocks, BrowserContext context, int pageNumber) {
        Locator mainTableBody = page.locator("table#highlowvalues tbody");
        ElementHandle mainTableBodyHandle = mainTableBody.elementHandle();
        DomHelper.tryScrollIntoView(mainTableBodyHandle);

        for (ElementHandle tbodyTr : mainTableBodyHandle.querySelectorAll("tr")) {
            List<ElementHandle> stockColumns = tbodyTr.querySelectorAll("td");

            // skip elements which are unknown
            String currentValue = stockColumns.get(1).innerText();
            if ("-".equals(currentValue) || currentValue.isEmpty()) {
                continue;
            }

            String stockName = stockColumns.getFirst().innerText();
            var link = tbodyTr.querySelector("td a");

            LOG.debug("Stock name: {}, link: {}", stockName, link.getAttribute("href"));

            Page stockPage = context.newPage();
            URI stockUri = getRelativeUri(link.getAttribute("href"));
            LOG.debug("Opening [{}] in new tab", stockUri);
            Response stockTabResponse = stockPage.navigate(stockUri.toString());

            if (stockTabResponse.status() != 200) {
                LOG.error("Could not open [{}], status = [{}]", stockUri, stockTabResponse.status());
                stockPage.close();
                continue;
            }

            Locator isinValueLocator = stockPage.locator("span.isin.value");
            String isin = null;

            for (ElementHandle elementHandle : isinValueLocator.elementHandles()) {
                LOG.debug("Stock [{}], ISIN found: [{}]", stockName, elementHandle.innerText());
                isin = elementHandle.innerText();
            }

            if (isin == null) {
                stockPage.close();
                continue;
            }

            Stock stock = new Stock(stockName, Isin.fromString(isin), Optional.of(getName()));
            LOG.debug("Found stock [{}] on page [{}].", stock, pageNumber);
            stocks.add(stock);
            stockPage.close();
        }
    }

    private URI getRelativeUri(String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }

        String scheme = uri().getScheme();
        String host = uri().getHost();
        int port = 443;
        if (uri().getPort() > 0) {
            port = uri().getPort();
        }

        return URI.create(scheme + "://" + host + ":" + port + path);
    }
}
