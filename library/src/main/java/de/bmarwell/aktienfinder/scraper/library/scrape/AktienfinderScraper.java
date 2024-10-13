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
package de.bmarwell.aktienfinder.scraper.library.scrape;

import static de.bmarwell.aktienfinder.scraper.library.scrape.ScrapeService.ResponseConstants.BEWERTUNG;
import static de.bmarwell.aktienfinder.scraper.library.scrape.ScrapeService.ResponseConstants.ZUSAMMENFASSUNG;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import de.bmarwell.aktienfinder.scraper.library.Stock;
import java.net.URI;
import java.util.HashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AktienfinderScraper {

    private static final Logger LOG = LoggerFactory.getLogger(AktienfinderScraper.class);

    private final BrowserContext browserContext;

    public AktienfinderScraper(BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    public void loadAndPopulate(Stock inStock, HashMap<String, String> xhrResponses, URI canonicalDataUrl) {
        try (Page page = this.browserContext.newPage()) {
            page.onDOMContentLoaded(pageContent -> LOG.debug("loaded: [{}]", pageContent.url()));
            var navigateOptions = new NavigateOptions();
            navigateOptions.setTimeout(15_000L);
            Consumer<Response> responseConsumer = response -> {
                LOG.debug("loaded data: [{}] for ISIN [{}].", response.url(), inStock.isin());

                if (response.url().endsWith("Scorings")) {
                    xhrResponses.put("Scorings", response.text());
                }

                if (response.url().endsWith("Performance")) {
                    xhrResponses.put("Performance", response.text());
                }

                if (response.url().endsWith("EarningsProfile")) {
                    xhrResponses.put("EarningsProfile", response.text());
                }

                if (response.url().contains("api/StockProfile?securityName=")) {
                    xhrResponses.put("StockProfile", response.text());
                }
            };
            page.onResponse(responseConsumer);

            var navResponse = page.navigate(canonicalDataUrl.toString(), navigateOptions);
            navResponse.finished();

            page.offResponse(responseConsumer);

            if (navResponse.status() != 200) {
                LOG.warn(
                        "could not retrieve stock data result of [{}], http status = [{}]",
                        canonicalDataUrl,
                        navResponse.status());
                LOG.warn("headers: [{}]", navResponse.headers());
                return;
            }

            boolean errorOccurred;
            for (int tries = 0; tries < 3; tries++) {
                try {
                    parseBewertungZusammenfassung(xhrResponses, page);
                    errorOccurred = false;
                } catch (PlaywrightException pe) {
                    LOG.error(
                            "Problem loading fazit for ISIN [{}], URL = [{}], message = [{}].",
                            inStock,
                            canonicalDataUrl,
                            pe.getMessage());
                    LOG.trace("Problem loading fazit for ISIN [{}], URL = [{}].", inStock, canonicalDataUrl, pe);
                    errorOccurred = true;
                }

                if (!errorOccurred) {
                    break;
                }
            }
        } catch (PlaywrightException pe) {
            LOG.error("Problem navigating to Aktienfinder", pe);
        }
    }

    private static void parseBewertungZusammenfassung(HashMap<String, String> xhrResponses, Page page) {
        var aktieKaufHeading = page.querySelector("#fazit-für-wen-ist-die-aktie-ein-kauf");

        if (aktieKaufHeading != null) {
            DomHelper.tryScrollIntoView(aktieKaufHeading);
        }

        var stockProfileSummary = page.querySelector(".stockprofile__summary_conclusion_row");

        if (stockProfileSummary != null) {
            DomHelper.tryScrollIntoView(stockProfileSummary);
        }

        var summaryInner = page.querySelector("div.stockprofile span.stockprofile__summary_row__inner");

        if (summaryInner != null) {
            xhrResponses.put(BEWERTUNG, summaryInner.innerText().strip());
        }

        var conclusionRow = page.querySelector(".stockprofile__summary_conclusion_row");

        if (conclusionRow != null) {
            String summaryClass = conclusionRow.getAttribute("class");

            if (summaryClass != null && summaryClass.contains("background--negative")) {
                xhrResponses.put(ZUSAMMENFASSUNG, "negative");
            }

            if (summaryClass != null && summaryClass.contains("background--negative-light")) {
                xhrResponses.put(ZUSAMMENFASSUNG, "negative-light");
            }

            if (summaryClass != null && summaryClass.contains("background--neutral-light")) {
                xhrResponses.put(ZUSAMMENFASSUNG, "neutral-light");
            }

            if (summaryClass != null && summaryClass.contains("background--positive")) {
                xhrResponses.put(ZUSAMMENFASSUNG, "positive");
            }

            if (summaryClass != null && summaryClass.contains("background--positive-light")) {
                xhrResponses.put(ZUSAMMENFASSUNG, "positive-light");
            }
        }
    }
}
