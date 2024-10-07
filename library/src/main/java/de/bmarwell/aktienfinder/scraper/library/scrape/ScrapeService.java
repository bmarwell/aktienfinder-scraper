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

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Playwright;
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.Anlagestrategie;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockBewertung;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockFazit;
import jakarta.json.Json;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrapeService {

    private static final Logger LOG = LoggerFactory.getLogger(ScrapeService.class);

    public List<AktienfinderStock> scrapeAll(Set<String> stockIsins) throws IOException {
        final var resultList = new ArrayList<AktienfinderStock>();

        for (String stockIsin : stockIsins) {
            Optional<AktienfinderStock> scrapedStock = this.scrape(stockIsin);

            if (scrapedStock.isPresent()) {
                resultList.add(scrapedStock.orElseThrow());
            }
        }

        return List.copyOf(resultList);
    }

    private Optional<AktienfinderStock> scrape(String stockIsin) throws IOException {
        Optional<URI> scrapeUrl = getCanonicalDataUrl(stockIsin);

        if (scrapeUrl.isEmpty()) {
            return Optional.empty();
        }

        final var canonicalDataUrl = scrapeUrl.orElseThrow();

        final var xhrResponses = new HashMap<String, String>();

        try (Playwright playwright = Playwright.create()) {
            final BrowserType browserType = playwright.chromium();

            try (Browser browser = browserType.launch()) {
                loadAndPopulate(browser, xhrResponses, canonicalDataUrl);

                // retry
                if (xhrResponses.get("StockProfile") == null) {
                    loadAndPopulate(browser, xhrResponses, canonicalDataUrl);
                }
                // retry 2
                if (xhrResponses.get("Scorings") == null) {
                    loadAndPopulate(browser, xhrResponses, canonicalDataUrl);
                }
            }
        }

        // now read
        final var stockProfile =
                new ByteArrayInputStream(xhrResponses.get("StockProfile").getBytes(StandardCharsets.UTF_8));
        final var stockDataReader = Json.createReader(stockProfile);
        final var stockData = stockDataReader.readObject();

        final var scoringResponse =
                new ByteArrayInputStream(xhrResponses.get("Scorings").getBytes(StandardCharsets.UTF_8));
        final var scoringReader = Json.createReader(scoringResponse);
        final var scoringData = scoringReader.readObject();
        final var stock = new Stock(stockData.getString("Name"), stockData.getString("Isin"));
        final var stockBewertung = new StockBewertung(
                stockData
                        .getJsonNumber("ReportedEpsCorrelation")
                        .bigDecimalValue()
                        .floatValue(),
                stockData
                        .getJsonNumber("AdjustedEpsCorrelation")
                        .bigDecimalValue()
                        .floatValue(),
                stockData.getJsonNumber("OcfCorrelation").bigDecimalValue().floatValue());
        // https://dividendenfinder.de/Securities/1112/Scorings
        final var anlagestrategie = new Anlagestrategie(
                scoringData
                        .getJsonObject("DividendEarningsScore")
                        .getJsonNumber("ResultScore")
                        .bigIntegerValue()
                        .shortValueExact(),
                scoringData
                        .getJsonObject("DividendGrowthScore")
                        .getJsonNumber("ResultScore")
                        .bigIntegerValue()
                        .shortValueExact(),
                scoringData
                        .getJsonObject("EarningGrowthScore")
                        .getJsonNumber("ResultScore")
                        .bigIntegerValue()
                        .shortValueExact());
        final var stockFazit = new StockFazit(anlagestrategie, "", "");
        final var aktienfinderStock = new AktienfinderStock(stock, stockBewertung, stockFazit);

        return Optional.of(aktienfinderStock);
    }

    private static void loadAndPopulate(Browser browser, HashMap<String, String> xhrResponses, URI canonicalDataUrl) {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        final var navigateOptions = new NavigateOptions();
        navigateOptions.setTimeout(10_000L);
        context.onResponse(response -> {
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
        });

        final var navResponse = page.navigate(canonicalDataUrl.toString(), navigateOptions);
        navResponse.finished();

        if (navResponse.status() != 200) {
            LOG.warn(
                    "could not retrieve stock data result of [{}], http status = [{}]",
                    canonicalDataUrl,
                    navResponse.status());
            LOG.warn("headers: [{}]", navResponse.headers());
            return;
        }

        final var aktieKaufHeading = page.querySelector("#fazit-für-wen-ist-die-aktie-ein-kauf");

        if (aktieKaufHeading != null) {
            aktieKaufHeading.scrollIntoViewIfNeeded();
        }
    }

    private static Optional<URI> getCanonicalDataUrl(String stockIsin) {
        final var searchUri = URI.create("https://dividendenfinder.de/api/StockProfile/List/" + stockIsin.strip());

        try (Playwright playwright = Playwright.create()) {
            final BrowserType browserType = playwright.chromium();
            try (Browser browser = browserType.launch()) {
                BrowserContext context = browser.newContext();
                Page page = context.newPage();
                final var navigateOptions = new NavigateOptions();
                navigateOptions.setTimeout(10_000L);
                page.onDOMContentLoaded(pageContent -> LOG.info("getting: [{}]", pageContent.url()));
                final var navResponse = page.navigate(searchUri.toString(), navigateOptions);
                navResponse.finished();

                if (navResponse.status() != 200) {
                    LOG.warn("could not retrieve result of [{}], http status = [{}]", searchUri, navResponse.status());

                    final var bodyOut = new ByteArrayOutputStream();
                    bodyOut.writeBytes(navResponse.body());
                    final var errorBody = bodyOut.toString(StandardCharsets.UTF_8);

                    final var headers = navResponse.headers();

                    LOG.warn("Reason: [{}], [{}]", errorBody, headers);

                    return Optional.empty();
                }

                // [{"Name":"NVIDIA","Isin":"US67066G1040","Symbol":"NVDA"}]
                final var body = new ByteArrayInputStream(navResponse.body());
                final var jsonReader = Json.createReaderFactory(Map.of()).createReader(body);

                final var resultItem = jsonReader.readObject().asJsonObject();
                final var resultIsin = resultItem.getString("Isin");

                if (stockIsin.equals(resultIsin)) {
                    final var securityName = resultItem.getString("Name");
                    final var uri = URI.create("https://aktienfinder.net/aktien-profil/" + securityName + "-Aktie");

                    return Optional.of(uri);
                }
            }
        } catch (RuntimeException httpEx) {
            LOG.error(
                    "unable to retrieve aktien name for ISIN [{}], URL=[{}], unknown error.",
                    stockIsin,
                    searchUri,
                    httpEx);
            return Optional.empty();
        }

        return Optional.empty();
    }
}
