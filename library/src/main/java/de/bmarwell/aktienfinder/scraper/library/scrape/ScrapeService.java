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

import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
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

        try (var httpClient = HttpClient.newHttpClient()) {
            final var request = HttpRequest.newBuilder()
                    .uri(canonicalDataUrl)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            final var inputStreamBodyHandler = BodyHandlers.ofInputStream();

            final var streamHttpResponse = httpClient.send(request, inputStreamBodyHandler);

            if (streamHttpResponse.statusCode() != 200) {
                LOG.warn(
                        "could not retrieve result of [{}], http status = [{}]",
                        canonicalDataUrl,
                        streamHttpResponse.statusCode());
                return Optional.empty();
            }

            final var jsonReader = Json.createReader(streamHttpResponse.body());
            System.out.println(jsonReader.readObject());

        } catch (IOException | InterruptedException httpEx) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static Optional<URI> getCanonicalDataUrl(String stockIsin) throws MalformedURLException {
        final var searchUri = URI.create(
                "https://dividendenfinder.de/Securities/Find?ex=true&length=8&searchTerm=" + stockIsin.strip());
        try (var httpClient = HttpClient.newHttpClient()) {
            final var request = HttpRequest.newBuilder()
                    .header("accept", "application/json")
                    .GET()
                    .uri(searchUri)
                    .build();
            final var responseBodyHandler = BodyHandlers.ofInputStream();

            final var stringHttpResponse = httpClient.send(request, responseBodyHandler);

            if (stringHttpResponse.statusCode() != 200) {
                LOG.warn(
                        "could not retrieve result of [{}], http status = [{}]",
                        searchUri,
                        stringHttpResponse.statusCode());
                return Optional.empty();
            }

            // [{"Name":"NVIDIA","Isin":"US67066G1040","Symbol":"NVDA"}]
            final var jsonReader = Json.createReaderFactory(Map.of()).createReader(stringHttpResponse.body());
            final var topLevelArray = jsonReader.readArray();
            for (JsonValue jsonValue : topLevelArray) {
                if (jsonValue.getValueType() != ValueType.OBJECT) {
                    continue;
                }

                final var resultItem = jsonValue.asJsonObject();
                final var resultIsin = resultItem.getString("Isin");

                if (stockIsin.equals(resultIsin)) {
                    final var securityName = resultItem.getString("Name");
                    final var uri = URI.create(
                            "https://dividendenfinder.de/api/StockProfile?securityName=" + securityName + "&lang=de");

                    return Optional.of(uri);
                }
            }

        } catch (IOException | InterruptedException httpEx) {
            return Optional.empty();
        }

        return Optional.empty();
    }
}
