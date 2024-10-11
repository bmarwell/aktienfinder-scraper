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
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache.Instance;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.Anlagestrategie;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.FinanzenNetRisiko;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockBewertung;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.StockFazit;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrapeService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ScrapeService.class);

    private final ExecutorService executor = Executors.newWorkStealingPool(ExecutorHelper.getNumberThreads());

    private final PoorMansCache<Playwright> browserCache =
            new PoorMansCache<>(ExecutorHelper.getNumberThreads(), this::createPlaywright);

    public List<AktienfinderStock> scrapeAll(Set<Stock> stockIsins) {
        var resultList = new ArrayList<AktienfinderStock>();
        var threads = new ArrayList<Future<Optional<AktienfinderStock>>>();

        for (Stock stock : stockIsins) {
            var scraperThread = executor.submit(() -> this.scrape(stock));
            threads.add(scraperThread);
        }

        for (var thread : threads) {
            try {
                Optional<AktienfinderStock> aktienfinderStock = thread.get();
                aktienfinderStock.ifPresent(resultList::add);
            } catch (CancellationException | ExecutionException | InterruptedException ex) {
                LOG.warn("Thread not finished: [{}]", thread, ex);
            }
        }

        return List.copyOf(resultList);
    }

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
            Playwright playwright = playwrightInstance.instance();

            try (Browser browser = playwright.chromium().launch();
                    BrowserContext browserContext = browser.newContext()) {
                loadAndPopulate(inStock, browserContext, xhrResponses, canonicalDataUrl);

                // retry
                if (xhrResponses.get("StockProfile") == null) {
                    loadAndPopulate(inStock, browserContext, xhrResponses, canonicalDataUrl);
                }
                // retry 2
                if (xhrResponses.get("Scorings") == null) {
                    loadAndPopulate(inStock, browserContext, xhrResponses, canonicalDataUrl);
                }

                finanzenNetRisiko = getFinanzenNetRisiko(inStock, browserContext);
            }
        } catch (Exception autoCloseEx) {
            LOG.error("Problem re-using playwright", autoCloseEx);
        }

        if (xhrResponses.get("StockProfile") == null) {
            LOG.warn("empty StockProfile for stockIsin [{}]", inStock);
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

    private FinanzenNetRisiko getFinanzenNetRisiko(Stock inStock, BrowserContext browserContext) {
        Optional<URI> finanzenNetUrl = getFinanzenNetUrl(inStock, browserContext);

        if (finanzenNetUrl.isEmpty()) {
            return FinanzenNetRisiko.empty();
        }

        Optional<URI> finanzenNetRisikoUri =
                getFinanzenNetRisikoUri(inStock, browserContext, finanzenNetUrl.orElseThrow());

        if (finanzenNetRisikoUri.isEmpty()) {
            return FinanzenNetRisiko.empty();
        }

        URI finanzenNetStockRisikoUri = finanzenNetRisikoUri.orElseThrow();
        LOG.info("Guessing URI: " + finanzenNetStockRisikoUri);

        return doGetFinanzenNetRisikoDetails(inStock, browserContext, finanzenNetStockRisikoUri);
    }

    private FinanzenNetRisiko doGetFinanzenNetRisikoDetails(
            Stock inStock, BrowserContext browserContext, URI finanzenNetStockRisikoUri) {
        Page page = null;

        try {
            page = browserContext.newPage();
            Response navigationResponse = page.navigate(finanzenNetStockRisikoUri.toString());

            if (navigationResponse.status() != 200) {
                LOG.info(
                        "Cannot navigate to [{}], status = {}", finanzenNetStockRisikoUri, navigationResponse.status());
                return FinanzenNetRisiko.empty();
            }

            Optional<String> risiko = Optional.empty();
            Optional<String> risikoBegruendung = Optional.empty();
            Optional<BigDecimal> beta = Optional.empty();

            // need to iterate result rows, as there are no IDs or classes which could be helpful
            List<ElementHandle> tableRows = page.querySelectorAll(".table--headline-first-col tbody tr");
            for (ElementHandle tableRow : tableRows) {
                List<ElementHandle> tdElements = tableRow.querySelectorAll("td");

                if (tdElements.size() < 2) {
                    continue;
                }

                if (tdElements.getFirst().innerText().contains("Risiko")) {
                    risiko = Optional.ofNullable(tdElements.get(2).innerText());
                    risikoBegruendung = Optional.ofNullable(tdElements.get(3).innerText());
                }

                if (tdElements.getFirst().innerText().contains("Beta")) {
                    String betaText = tdElements.get(1).innerText().strip().replaceAll(",", ".");

                    try {
                        BigDecimal bigDecimal = new BigDecimal(betaText);
                        beta = Optional.of(bigDecimal);
                    } catch (NumberFormatException nfe) {
                        LOG.warn(
                                "Beta for Stock {} is not a decimal value: {}, url [{}]",
                                finanzenNetStockRisikoUri,
                                betaText,
                                finanzenNetStockRisikoUri);
                    }
                }
            }

            return new FinanzenNetRisiko(risiko, risikoBegruendung, beta);

        } finally {
            if (page != null) {
                page.close();
            }
        }
    }

    /**
     * Constructs and returns an optional URI for the risk analysis page of a given stock.
     * If the input stock URI meets certain conditions, it transforms the URI to point
     * to the risk analysis page. If not, it attempts to navigate to the stock's page
     * and fetch additional risk analysis information.
     *
     * @param inStock the stock for which the URI is being generated; null values should be avoided
     * @param browserContext the browser context used for navigating and fetching web pages; should not be null
     * @param stockUri the URI of the stock page; must be a valid, non-null URI
     * @return an {@code Optional<URI>} pointing to the risk analysis page if the transformation
     *         is possible, otherwise an empty {@code Optional}. It can also return empty if there
     *         are errors during navigation and page fetch attempts.
     */
    private Optional<URI> getFinanzenNetRisikoUri(Stock inStock, BrowserContext browserContext, URI stockUri) {
        Page page = null;

        String stockUriString = stockUri.toString();

        if (stockUriString.contains("/aktien/") && stockUriString.endsWith("-aktie")) {
            String modifiedUri =
                    stockUriString.replaceAll("/aktien/", "/risikoanalyse/").replaceAll("-aktie", "");
            return Optional.of(URI.create(modifiedUri));
        }

        try {
            page = browserContext.newPage();
            page.navigate(stockUriString);

            throw new UnsupportedOperationException(
                    "Not yet implemented: navigate to risikoanalyse for " + stockUriString);
        } finally {
            if (page != null) {
                page.close();
            }
        }
    }

    /**
     * Constructs and returns an optional {@code URI} for a stock from Finanzen.net.
     * <p>
     * This method attempts to navigate to a specific JSON endpoint on Finanzen.net,
     * parses the JSON response, and retrieves the URL corresponding to the provided stock's ISIN.
     * </p>
     *
     * @param inStock the stock for which the URL is being generated; must not be {@code null}.
     * @param browserContext the browser context used for navigating and fetching web pages; must not be {@code null}.
     * @return an {@code Optional<URI>} containing the URL if found, otherwise an empty {@code Optional}.
     */
    private Optional<URI> getFinanzenNetUrl(Stock inStock, BrowserContext browserContext) {
        URI scrapeUri = URI.create("https://www.finanzen.net/");
        String strippedIsin = inStock.isin().strip();

        // "https://www.finanzen.net/suggest/finde/jsonv2?max_results=25&Keywords_mode=APPROX&Keywords=%1$s&query=%1$s&bias=100",
        try (Page page = browserContext.newPage()) {
            Response navigate = page.navigate(scrapeUri.toString());

            // TODO: find cookie banner

            Locator inputElement = page.locator("input#suggest-search-desktop-input");

            if (inputElement == null || inputElement.all().isEmpty()) {
                LOG.warn("could not find search box");
                return Optional.empty();
            }

            Response navigate2 = page.waitForResponse(
                    response -> response.url().contains("t/suggest/finde/jsonv2")
                            && response.url().contains("query=" + strippedIsin),
                    () -> inputElement.fill(strippedIsin));

            if (navigate2.status() != 200) {
                LOG.info("Cannot navigate to [{}], status = {}", scrapeUri, navigate.status());
                return Optional.empty();
            }

            String jsonResponse = navigate2.text();

            // Parse the JSON response
            var jsonReader = Json.createReader(new StringReader(jsonResponse));
            var jsonArray = jsonReader.readArray();

            // Look for the element with the field "n" having the value "Aktien"
            for (var jsonValue : jsonArray) {
                if (jsonValue instanceof JsonObject aktienResult
                        && "Aktien".equals(aktienResult.getString("n", ""))
                        && aktienResult.get("il") instanceof JsonArray aktienList) {
                    // Element found, do something with aktienResult if needed
                    LOG.debug("Found element with 'n' = 'Aktien': {}", aktienResult);
                    for (JsonValue aktienJsonResult : aktienList) {
                        if (!(aktienJsonResult instanceof JsonObject aktienJson)) {
                            continue;
                        }

                        if (aktienJson.getString("isin").equals(strippedIsin)) {
                            return Optional.of(URI.create(aktienJson.getString("u")));
                        }
                    }
                    break;
                }
            }

            return Optional.empty();
        }
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
            Stock inStock, BrowserContext context, HashMap<String, String> xhrResponses, URI canonicalDataUrl) {
        Page page = context.newPage();
        page.onDOMContentLoaded(pageContent -> LOG.debug("loaded: [{}]", pageContent.url()));
        var navigateOptions = new NavigateOptions();
        navigateOptions.setTimeout(10_000L);
        context.onResponse(response -> {
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

    private static Optional<URI> getCanonicalDataUrl(Stock stock) {
        var searchUri = URI.create("https://dividendenfinder.de/api/StockProfile/List/"
                + stock.isin().strip());

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch()) {
                BrowserContext context = browser.newContext();
                Page page = context.newPage();
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
        } catch (RuntimeException httpEx) {
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

    static class ResponseConstants {
        static final String ZUSAMMENFASSUNG = "bewertungsfarbe";
        static final String BEWERTUNG = "bewertungstext";
    }
}
