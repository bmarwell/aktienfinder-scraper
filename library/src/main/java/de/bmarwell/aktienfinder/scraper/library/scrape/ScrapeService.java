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

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache.Instance;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.Anlagestrategie;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.FinanzenNetRisiko;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockBewertung;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockFazit;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrapeService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ScrapeService.class);

    private final ExecutorService executor =
            Executors.newFixedThreadPool(ExecutorHelper.getNumberThreads(), (Runnable runnable) -> {
                Thread thread = new Thread(runnable);
                thread.setName(String.format("scrape-thread-%d", thread.threadId()));
                return thread;
            });

    private final PoorMansCache<Playwright> browserCache =
            new PoorMansCache<>(ExecutorHelper.getNumberThreads(), this::createPlaywright);

    /**
     * Scrapes data for a set of provided stocks asynchronously.
     * Each stock's data is processed in a separate thread to optimize performance.
     *
     * @param stockIsins a set of {@link Stock} objects that encapsulate information such as name and ISIN.
     * @return a list of {@code AktienfinderStock} objects containing the scraped data for the provided stocks.
     */
    public List<AktienfinderStock> scrapeAll(Set<Stock> stockIsins) {
        var resultList = new ArrayList<AktienfinderStock>();
        var threads = new ArrayList<Future<Optional<AktienfinderStock>>>();

        for (Stock stock : stockIsins) {
            var scraperThread = executor.submit(() -> this.scrape(stock));

            threads.add(scraperThread);
        }

        for (var thread : threads) {
            if (thread == null) {
                continue;
            }

            try {
                Optional<AktienfinderStock> aktienfinderStock = thread.get(30, TimeUnit.SECONDS);
                aktienfinderStock.ifPresent(resultList::add);
            } catch (TimeoutException e) {
                thread.cancel(true);
                LOG.warn("Thread timed out: [{}]", thread);
            } catch (CancellationException | ExecutionException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                LOG.warn("Thread not finished: [{}]", thread, ex);
            }
        }

        return List.copyOf(resultList);
    }

    /**
     * Scrapes detailed stock information for a given {@link Stock} by utilizing the Aktienfinder and FinanzenNet
     * scraping mechanisms.
     *
     * <p>This method attempts to retrieve a stock's canonical data URL, loads the stock's profile via Playwright,
     * and extracts the relevant financial data. If data extraction fails, it retries up to two times.</p>
     *
     * @param inStock The {@link Stock} object for which detailed information needs to be scraped.
     * @return An {@code Optional<AktienfinderStock>} containing detailed information about the stock if available; otherwise {@code Optional.empty()}.
     */
    private Optional<AktienfinderStock> scrape(Stock inStock) {
        Optional<URI> scrapeUrl = getCanonicalDataUrl(inStock);

        if (scrapeUrl.isEmpty()) {
            return Optional.empty();
        }

        var canonicalDataUrl = scrapeUrl.orElseThrow();

        var xhrResponses = new HashMap<String, String>();
        xhrResponses.put(BEWERTUNG, "unbewertet");
        xhrResponses.put(ZUSAMMENFASSUNG, "neutral");

        FinanzenNetRisiko finanzenNetRisiko = FinanzenNetRisiko.empty();

        try (Instance<Playwright> playwrightInstance = this.browserCache.getBlocking()) {
            try (Browser browser = playwrightInstance.instance().chromium().launch();
                    BrowserContext browserContext = browser.newContext(contextOptions())) {

                AktienfinderScraper aktienfinderScraper = new AktienfinderScraper(browserContext);
                aktienfinderScraper.loadAndPopulate(inStock, xhrResponses, canonicalDataUrl);

                // retry
                if (xhrResponses.get("StockProfile") == null) {
                    aktienfinderScraper.loadAndPopulate(inStock, xhrResponses, canonicalDataUrl);
                }
                // retry 2
                if (xhrResponses.get("Scorings") == null) {
                    aktienfinderScraper.loadAndPopulate(inStock, xhrResponses, canonicalDataUrl);
                }

                FinanzenNetScraper finanzenNetScraper = new FinanzenNetScraper(browserContext);
                finanzenNetRisiko = finanzenNetScraper.getFinanzenNetRisiko(inStock);
            }
        } catch (PlaywrightException autoCloseEx) {
            LOG.error("Problem running playwright", autoCloseEx);
        } catch (TimeoutException | InterruptedException teEx) {
            LOG.error("Problem re-using playwright", teEx);
        } catch (Exception genericEx) {
            LOG.error("Problem with something too generic!", genericEx);
        }

        if (xhrResponses.get("StockProfile") == null) {
            LOG.warn("empty StockProfile for stock [{} - ISIN: {}]", inStock.name(), inStock.isin());

            return Optional.empty();
        }

        // now read
        var stockProfile =
                new ByteArrayInputStream(xhrResponses.get("StockProfile").getBytes(StandardCharsets.UTF_8));
        var stockDataReader = Json.createReader(stockProfile);
        var stockData = stockDataReader.readObject();

        var stock = new Stock(stockData.getString("Name"), stockData.getString("Isin"), inStock.index());
        double bilGewinn = -1;
        double bereinigterGewinn = -1;
        double operativerCashFlow = -1;

        if (stockData.get("ReportedEpsCorrelation") instanceof JsonNumber bilGewinnJson) {
            bilGewinn = bilGewinnJson.bigDecimalValue().doubleValue();
        } else {
            LOG.warn(
                    "StockProfile does not contain ReportedEpsCorrelation: [{}]",
                    stockData.get("ReportedEpsCorrelation"));
        }

        if (stockData.get("AdjustedEpsCorrelation") instanceof JsonNumber bereinigterGewinnJson) {
            bereinigterGewinn = bereinigterGewinnJson.bigDecimalValue().doubleValue();
        } else {
            LOG.warn(
                    "StockProfile does not contain AdjustedEpsCorrelation: [{}]",
                    stockData.get("AdjustedEpsCorrelation"));
        }

        if (stockData.get("OcfCorrelation") instanceof JsonNumber operativerCashFlowJson) {
            operativerCashFlow = operativerCashFlowJson.bigDecimalValue().doubleValue();
        } else {
            LOG.warn("StockProfile does not contain OcfCorrelation: [{}]", stockData.get("OcfCorrelation"));
        }

        var stockBewertung = new StockBewertung(bilGewinn, bereinigterGewinn, operativerCashFlow);

        // https://dividendenfinder.de/Securities/1112/Scorings
        Anlagestrategie anlagestrategie;
        if (xhrResponses.get("Scorings") == null) {
            anlagestrategie = new Anlagestrategie((short) -1, (short) -1, (short) -1);
        } else {
            anlagestrategie = getAnlageStrategieScorings(xhrResponses);
        }

        var stockFazit =
                new StockFazit(anlagestrategie, xhrResponses.get(BEWERTUNG), xhrResponses.get(ZUSAMMENFASSUNG));
        var aktienfinderStock = new AktienfinderStock(stock, stockBewertung, stockFazit, finanzenNetRisiko);

        LOG.debug("Aktienfinder Stock: [{}].", aktienfinderStock);

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

    private Optional<URI> getCanonicalDataUrl(Stock stock) {
        var searchUri = URI.create("https://dividendenfinder.de/api/StockProfile/List/"
                + stock.isin().strip());

        try (Instance<Playwright> playwright = this.browserCache.getBlocking()) {
            try (Browser browser = playwright.instance().chromium().launch();
                    BrowserContext context = browser.newContext(contextOptions());
                    Page page = context.newPage()) {
                var navigateOptions = new NavigateOptions();
                navigateOptions.setTimeout(10_000L);
                page.onDOMContentLoaded(pageContent -> LOG.debug("loaded: [{}]", pageContent.url()));
                var navResponse = page.navigate(searchUri.toString(), navigateOptions);
                navResponse.finished();

                if (navResponse.status() != 200) {
                    var bodyOut = new ByteArrayOutputStream();
                    bodyOut.writeBytes(navResponse.body());
                    var errorBody = bodyOut.toString(StandardCharsets.UTF_8);

                    LOG.warn(
                            "could not retrieve result of [{}], http status = [{}], body = [{}]",
                            searchUri,
                            navResponse.status(),
                            errorBody);

                    return Optional.empty();
                }

                // [{"Name":"NVIDIA","Isin":"US67066G1040","Symbol":"NVDA"}]
                var body = new ByteArrayInputStream(navResponse.body());
                var jsonReader = Json.createReaderFactory(Map.of()).createReader(body);

                var resultItem = jsonReader.readObject().asJsonObject();
                var resultIsin = resultItem.getString("Isin");

                if (stock.isin().equals(resultIsin)) {
                    var securityName = resultItem.getString("Name");
                    String urlSafeStockName = URLEncoder.encode(securityName, StandardCharsets.UTF_8)
                            .replaceAll("\\+", "%20");
                    var uri = URI.create("https://aktienfinder.net/aktien-profil/" + urlSafeStockName + "-Aktie");

                    return Optional.of(uri);
                }
            }
        } catch (Exception httpEx) {
            LOG.error(
                    "unable to retrieve aktien name for ISIN [{}], URL=[{}], unknown error.",
                    stock.isin(),
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

    private NewContextOptions contextOptions() {
        NewContextOptions newContextOptions = new NewContextOptions();
        newContextOptions.setAcceptDownloads(false);
        newContextOptions.setLocale("de-DE");
        newContextOptions.setHasTouch(false);
        newContextOptions.setTimezoneId("Europe/Berlin");
        newContextOptions.setUserAgent("Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0");

        return newContextOptions;
    }

    static class ResponseConstants {
        static final String ZUSAMMENFASSUNG = "bewertungsfarbe";
        static final String BEWERTUNG = "bewertungstext";
    }
}
