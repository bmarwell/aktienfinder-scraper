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

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.ClickOptions;
import com.microsoft.playwright.Locator.PressSequentiallyOptions;
import com.microsoft.playwright.Locator.WaitForOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Page.WaitForResponseOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import de.bmarwell.aktienfinder.scraper.value.FinanzenNetRisiko;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinanzenNetScraper {

    private static final Logger LOG = LoggerFactory.getLogger(FinanzenNetScraper.class);
    private final BrowserContext browserContext;
    private boolean cookiesAccepted = false;

    public FinanzenNetScraper(BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    public FinanzenNetRisiko getFinanzenNetRisiko(Stock inStock) {
        Throwable cookiesAcceptedThrowable = null;

        for (int i = 0; i < 2; i++) {
            if (cookiesAccepted) {
                break;
            }

            cookiesAcceptedThrowable = ensureCookiesAccepted();
        }

        if (!cookiesAccepted) {
            if (cookiesAcceptedThrowable != null) {
                throw new IllegalStateException("could not accept cookies", cookiesAcceptedThrowable);
            } else {
                throw new IllegalStateException("could not accept cookies");
            }
        }

        Optional<URI> finanzenNetUrl = getFinanzenNetUrl(inStock);

        if (finanzenNetUrl.isEmpty()) {
            throw new IllegalStateException("No finanzenNetUrl found for stock " + inStock);
        }

        Optional<URI> finanzenNetRisikoUri = getFinanzenNetRisikoUri(browserContext, finanzenNetUrl.orElseThrow());

        if (finanzenNetRisikoUri.isEmpty()) {
            throw new IllegalStateException("No finanzenNetRisikoUri found for stock " + inStock);
        }

        URI finanzenNetStockRisikoUri = finanzenNetRisikoUri.orElseThrow();
        LOG.trace("Guessing URI: " + finanzenNetStockRisikoUri);

        return doGetFinanzenNetRisikoDetails(browserContext, finanzenNetStockRisikoUri);
    }

    private FinanzenNetRisiko doGetFinanzenNetRisikoDetails(
            BrowserContext browserContext, URI finanzenNetStockRisikoUri) {
        try (Page page = browserContext.newPage()) {
            Response navigationResponse = page.navigate(finanzenNetStockRisikoUri.toString());

            if (navigationResponse.status() != 200) {
                throw new IllegalStateException("Cannot navigate to " + finanzenNetStockRisikoUri + ", status = "
                        + navigationResponse.status());
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
        }
    }

    /**
     * Constructs and returns an optional URI for the risk analysis page of a given stock.
     * If the input stock URI meets certain conditions, it transforms the URI to point
     * to the risk analysis page. If not, it attempts to navigate to the stock's page
     * and fetch additional risk analysis information.
     *
     * @param browserContext the browser context used for navigating and fetching web pages; should not be null
     * @param stockUri the URI of the stock page; must be a valid, non-null URI
     * @return an {@code Optional<URI>} pointing to the risk analysis page if the transformation
     *         is possible, otherwise an empty {@code Optional}. It can also return empty if there
     *         are errors during navigation and page fetch attempts.
     */
    private Optional<URI> getFinanzenNetRisikoUri(BrowserContext browserContext, URI stockUri) {
        String stockUriString = stockUri.toString();

        if (stockUriString.contains("/aktien/") && stockUriString.endsWith("-aktie")) {
            String modifiedUri =
                    stockUriString.replaceAll("/aktien/", "/risikoanalyse/").replaceAll("-aktie", "");
            return Optional.of(URI.create(modifiedUri));
        }

        try (Page page = browserContext.newPage()) {
            page.navigate(stockUriString);

            throw new UnsupportedOperationException(
                    "Not yet implemented: navigate to risikoanalyse for " + stockUriString);
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
     * @return an {@code Optional<URI>} containing the URL if found, otherwise an empty {@code Optional}.
     */
    private Optional<URI> getFinanzenNetUrl(Stock inStock) {
        URI scrapeUri = URI.create("https://www.finanzen.net/");
        String strippedIsin = inStock.isin().value().strip();

        // "https://www.finanzen.net/suggest/finde/jsonv2?max_results=25&Keywords_mode=APPROX&Keywords=%1$s&query=%1$s&bias=100",
        try (Page page = browserContext.newPage()) {
            Response navigate = page.navigate(scrapeUri.toString());

            if (navigate.status() != 200) {
                LOG.info(
                        "Cannot navigate to landing page [{}] for isin {}, status = {}",
                        scrapeUri,
                        strippedIsin,
                        navigate.status());
                var so = new ScreenshotOptions();
                so.setPath(Paths.get("/tmp/" + strippedIsin + "_landing.png"));
                page.screenshot(so);

                throw new IllegalStateException("Cannot navigate to landing page " + scrapeUri + ", status = "
                        + navigate.status() + ". See " + so.path);
            }

            // type something into the search field and get a response.
            String jsonResponse = getSearchJsonResponse(page, strippedIsin, scrapeUri);

            // Parse the JSON response
            var jsonReader = Json.createReader(new StringReader(jsonResponse));
            var rootObject = jsonReader.readObject();

            if (!rootObject.containsKey("it")) {
                throw new IllegalStateException("Page  [" + scrapeUri + "] does not contain 'it': " + rootObject);
            }

            JsonArray it = rootObject.getJsonArray("it");

            // Look for the element with the field "n" having the value "Aktien"
            for (var jsonValue : it) {
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
                            String uri = aktienJson.getString("u");
                            LOG.info("found uri {} for isin {} in result: {}", uri, strippedIsin, aktienJson);
                            return Optional.of(URI.create(uri));
                        }
                    }
                    break;
                }
            }

            return Optional.empty();
        }
    }

    private static String getSearchJsonResponse(Page page, String strippedIsin, URI scrapeUri) {
        Locator inputElement = page.locator("input#suggest-search-desktop-input");

        if (inputElement == null || inputElement.all().isEmpty()) {
            LOG.warn("could not find search box");
            var so = new ScreenshotOptions();
            so.setPath(Paths.get("/tmp/" + strippedIsin + "_searchbox.png"));
            page.screenshot(so);

            throw new IllegalStateException("could not find search box on " + scrapeUri + ". See " + so.path);
        }

        inputElement.waitFor(new WaitForOptions().setTimeout(1_000L).setState(WaitForSelectorState.VISIBLE));

        Response navigate2;
        Consumer<Response> responseLogger = createResponseLogger("WaitForSuggestFindeJsonV2");

        try {
            // try to click away the google popup
            for (Frame frame : page.frames()) {
                Locator locator = frame.locator("div#close");
                if (locator == null || !locator.isVisible()) {
                    continue;
                }

                frame.click("div#close");
            }

            Locator inputElementReloaded = page.locator("input#suggest-search-desktop-input");
            inputElementReloaded.waitFor(
                    new WaitForOptions().setTimeout(1_000L).setState(WaitForSelectorState.VISIBLE));

            page.onResponse(responseLogger);
            navigate2 = page.waitForResponse(
                    response -> response.url().contains("t/suggest/finde/jsonv2")
                            && response.url().contains("query=" + strippedIsin),
                    () -> {
                        inputElementReloaded.click(
                                new ClickOptions().setDelay(10L).setTimeout(1_000L));
                        page.locator("div.suggest-search__headline")
                                .waitFor(
                                        new WaitForOptions().setTimeout(5_000L).setState(WaitForSelectorState.VISIBLE));
                        inputElementReloaded.pressSequentially(
                                strippedIsin, new PressSequentiallyOptions().setDelay(100L));
                        inputElementReloaded.blur();
                    });
        } catch (PlaywrightException pe) {
            var so = new ScreenshotOptions();
            so.setPath(Paths.get("/tmp/" + strippedIsin + "_waiting_jsonv2.png"));
            page.screenshot(so);

            LOG.error("Problem finding request jsonv2, see: [{}]", so.path, pe);

            throw new IllegalStateException("Problem finding request jsonv2, see " + so.path);
        } finally {
            page.offResponse(responseLogger);
        }

        if (navigate2.status() != 200) {
            LOG.info("Cannot retrieve finde/jsonv2 [{}], status = {}", scrapeUri, navigate2.status());
            throw new IllegalStateException(
                    "Cannot retrieve finde/jsonv2 [" + scrapeUri + "], status = " + navigate2.status() + ".");
        }

        return navigate2.text();
    }

    private static Consumer<Response> createResponseLogger(String id) {
        return (Response r) -> {
            if (!r.url().startsWith("https://www.finanzen.net/")) {
                return;
            }
            LOG.debug("input loaded after action [{}]: [{}]", id, r.url());
        };
    }

    private @Nullable Throwable ensureCookiesAccepted() {
        if (cookiesAccepted) {
            return null;
        }

        try (Page page = browserContext.newPage()) {
            var navigateOptions = new NavigateOptions();
            navigateOptions.setTimeout(10_000L).setWaitUntil(WaitUntilState.DOMCONTENTLOADED);
            try {
                Response navigate = page.navigate("https://www.finanzen.net/", navigateOptions);

                if (navigate.status() != 200) {
                    LOG.error("could not open finanzen.net");
                }

            } catch (TimeoutError timeoutError) {
                page.screenshot(new ScreenshotOptions().setPath(Paths.get("/tmp/finanzen.net_cookie_timeout.png")));
                throw timeoutError;
            }

            boolean buttonClicked = clickFinanzenNetCookieAcceptIfExists(page);

            if (buttonClicked) {
                cookiesAccepted = true;
            }

            return null;
        } catch (PlaywrightException pe) {
            LOG.error("could not open finanzen.net", pe);

            return pe;
        }
    }

    /**
     * Attempts to click the "cookie accept" button on the given webpage if it exists. Waits for
     * reload after accepting.
     *
     * @param page the Playwright {@code Page} object representing the webpage.
     * @return {@code true} if a button was clicked.
     */
    private boolean clickFinanzenNetCookieAcceptIfExists(Page page) {
        for (Frame frame : page.frames()) {
            if (frame.isDetached()) {
                continue;
            }

            try {
                FrameLocator iframe = frame.frameLocator("iframe[title=\"SP Consent Message\"]");
                LOG.trace("iframe: {}", iframe);
                Locator alleAkzeptieren = iframe.locator("button[title=\"Alle akzeptieren\"]");
                if (alleAkzeptieren == null) {
                    LOG.info("akzeptieren button not found");
                    return false;
                }

                WaitForOptions waitForOptions =
                        new WaitForOptions().setTimeout(5_000L).setState(WaitForSelectorState.VISIBLE);
                alleAkzeptieren.waitFor(waitForOptions);

                if (!alleAkzeptieren.isVisible()) {
                    LOG.info("akzeptieren button found but not visible");
                    return false;
                }

                if (!alleAkzeptieren.isEnabled()) {
                    LOG.info("akzeptieren button found but not enabled");
                    return false;
                }

                ClickOptions opts = new ClickOptions();
                opts.setTimeout(500L);

                try {
                    page.waitForResponse(
                            "https://www.finanzen.net/",
                            new WaitForResponseOptions().setTimeout(5_000L),
                            () -> alleAkzeptieren.click(opts));
                } catch (PlaywrightException pe) {
                    LOG.info("did click trigger a reload?", pe);
                }

                return true;
            } catch (PlaywrightException pe) {
                LOG.info("no accept button to click?", pe);
            }
        }
        return false;
    }
}
