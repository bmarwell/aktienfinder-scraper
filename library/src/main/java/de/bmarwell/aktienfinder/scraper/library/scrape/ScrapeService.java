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
import com.microsoft.playwright.PlaywrightException;
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache.Instance;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.Anlagestrategie;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockBewertung;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockFazit;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrapeService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ScrapeService.class);

    ExecutorService executor = Executors.newWorkStealingPool(4);

    PoorMansCache<Playwright> browserCache = new PoorMansCache<>(4, this::createPlaywright);

    public List<AktienfinderStock> scrapeAll(Set<String> stockIsins) {
        var resultList = new ArrayList<AktienfinderStock>();

        for (String stockIsin : stockIsins) {
            Optional<AktienfinderStock> scrapedStock = this.scrape(stockIsin);

            if (scrapedStock.isPresent()) {
                resultList.add(scrapedStock.orElseThrow());
            }
        }

        return List.copyOf(resultList);
    }

    private Optional<AktienfinderStock> scrape(String stockIsin) {
        Optional<URI> scrapeUrl = getCanonicalDataUrl(stockIsin);

        if (scrapeUrl.isEmpty()) {
            return Optional.empty();
        }

        var canonicalDataUrl = scrapeUrl.orElseThrow();

        var xhrResponses = new HashMap<String, String>();

        try (Instance<Playwright> playwrightInstance = this.browserCache.get()) {
            BrowserType browserType = playwrightInstance.instance().chromium();

            try (Browser browser = browserType.launch()) {
                loadAndPopulate(stockIsin, browser, xhrResponses, canonicalDataUrl);

                // retry
                if (xhrResponses.get("StockProfile") == null) {
                    loadAndPopulate(stockIsin, browser, xhrResponses, canonicalDataUrl);
                }
                // retry 2
                if (xhrResponses.get("Scorings") == null) {
                    loadAndPopulate(stockIsin, browser, xhrResponses, canonicalDataUrl);
                }
            }
        } catch (Exception autoCloseEx) {
            LOG.error("Problem re-using playwright", autoCloseEx);
        }

        if (xhrResponses.get("StockProfile") == null) {
            LOG.warn("empty StockProfile for stockIsin [{}]", stockIsin);
            return Optional.empty();
        }

        // now read
        var stockProfile =
                new ByteArrayInputStream(xhrResponses.get("StockProfile").getBytes(StandardCharsets.UTF_8));
        var stockDataReader = Json.createReader(stockProfile);
        var stockData = stockDataReader.readObject();

        var stock = new Stock(stockData.getString("Name"), stockData.getString("Isin"), Optional.empty());
        var stockBewertung = new StockBewertung(
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
        Anlagestrategie anlagestrategie;
        if (xhrResponses.get("Scorings") == null) {
            anlagestrategie = new Anlagestrategie((short) -1, (short) -1, (short) -1);
        } else {
            anlagestrategie = getAnlageStrategieScorings(xhrResponses);
        }

        var stockFazit = new StockFazit(anlagestrategie, "", "");
        var aktienfinderStock = new AktienfinderStock(stock, stockBewertung, stockFazit);

        return Optional.of(aktienfinderStock);
    }

    private static Anlagestrategie getAnlageStrategieScorings(HashMap<String, String> xhrResponses) {
        var scoringResponse =
                new ByteArrayInputStream(xhrResponses.get("Scorings").getBytes(StandardCharsets.UTF_8));
        var scoringReader = Json.createReader(scoringResponse);
        var scoringData = scoringReader.readObject();

        LOG.debug("scoringData: {}", scoringData);

        JsonValue dividendEarningsScore = scoringData.get("DividendEarningsScore");
        JsonValue dividendGrowthScore = scoringData.get("DividendGrowthScore");
        JsonValue earningGrowthScore = scoringData.get("EarningGrowthScore");

        return new Anlagestrategie(
                (dividendEarningsScore != null && dividendEarningsScore.getValueType() != ValueType.NULL)
                        ? dividendEarningsScore
                                .asJsonObject()
                                .getJsonNumber("ResultScore")
                                .bigIntegerValue()
                                .shortValueExact()
                        : (short) -1,
                (dividendGrowthScore != null && dividendGrowthScore.getValueType() != ValueType.NULL)
                        ? dividendGrowthScore
                                .asJsonObject()
                                .getJsonNumber("ResultScore")
                                .bigIntegerValue()
                                .shortValueExact()
                        : (short) -1,
                (earningGrowthScore != null && earningGrowthScore.getValueType() != ValueType.NULL)
                        ? earningGrowthScore
                                .asJsonObject()
                                .getJsonNumber("ResultScore")
                                .bigIntegerValue()
                                .shortValueExact()
                        : (short) -1);
    }

    private static void loadAndPopulate(
            String stockIsin, Browser browser, HashMap<String, String> xhrResponses, URI canonicalDataUrl) {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        page.onDOMContentLoaded(pageContent -> LOG.info("loaded: [{}]", pageContent.url()));
        var navigateOptions = new NavigateOptions();
        navigateOptions.setTimeout(10_000L);
        context.onResponse(response -> {
            LOG.debug("loaded data: [{}] for ISIN [{}].", response.url(), stockIsin);

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

        var navResponse = page.navigate(canonicalDataUrl.toString(), navigateOptions);
        navResponse.finished();

        if (navResponse.status() != 200) {
            LOG.warn(
                    "could not retrieve stock data result of [{}], http status = [{}]",
                    canonicalDataUrl,
                    navResponse.status());
            LOG.warn("headers: [{}]", navResponse.headers());
            return;
        }

        var aktieKaufHeading = page.querySelector("#fazit-für-wen-ist-die-aktie-ein-kauf");

        if (aktieKaufHeading != null) {
            try {
                aktieKaufHeading.scrollIntoViewIfNeeded();
            } catch (PlaywrightException pe) {
                LOG.error("Problem loading fazit for ISIN [{}], URL = [{}].", stockIsin, canonicalDataUrl, pe);
            }
        }
    }

    private static Optional<URI> getCanonicalDataUrl(String stockIsin) {
        var searchUri = URI.create("https://dividendenfinder.de/api/StockProfile/List/" + stockIsin.strip());

        try (Playwright playwright = Playwright.create()) {
            BrowserType browserType = playwright.chromium();
            try (Browser browser = browserType.launch()) {
                BrowserContext context = browser.newContext();
                Page page = context.newPage();
                var navigateOptions = new NavigateOptions();
                navigateOptions.setTimeout(10_000L);
                page.onDOMContentLoaded(pageContent -> LOG.debug("loaded: [{}]", pageContent.url()));
                var navResponse = page.navigate(searchUri.toString(), navigateOptions);
                navResponse.finished();

                if (navResponse.status() != 200) {
                    LOG.warn("could not retrieve result of [{}], http status = [{}]", searchUri, navResponse.status());

                    var bodyOut = new ByteArrayOutputStream();
                    bodyOut.writeBytes(navResponse.body());
                    var errorBody = bodyOut.toString(StandardCharsets.UTF_8);

                    var headers = navResponse.headers();

                    LOG.warn("Reason: [{}], [{}]", errorBody, headers);

                    return Optional.empty();
                }

                // [{"Name":"NVIDIA","Isin":"US67066G1040","Symbol":"NVDA"}]
                var body = new ByteArrayInputStream(navResponse.body());
                var jsonReader = Json.createReaderFactory(Map.of()).createReader(body);

                var resultItem = jsonReader.readObject().asJsonObject();
                var resultIsin = resultItem.getString("Isin");

                if (stockIsin.equals(resultIsin)) {
                    var securityName = resultItem.getString("Name");
                    String urlSafeStockName = URLEncoder.encode(securityName, StandardCharsets.UTF_8)
                            .replaceAll("\\+", "%20");
                    var uri = URI.create("https://aktienfinder.net/aktien-profil/" + urlSafeStockName + "-Aktie");

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

    @Override
    public void close() throws Exception {
        this.executor.shutdown();
        this.browserCache.close();
        this.executor.shutdownNow();
    }

    private Playwright createPlaywright() {
        return Playwright.create();
    }
}
