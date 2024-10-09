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
package de.bmarwell.aktienfinder.scraper.app;

import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.export.ExportService;
import de.bmarwell.aktienfinder.scraper.library.scrape.ScrapeService;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "scrape", header = "Scrapes stocks from aktienfinder.net")
public class ScrapeCommand implements Callable<Integer> {

    @Option(
            names = {"-i", "--stocks"},
            description = "stock isins",
            split = ",")
    Set<String> stockIsins = new HashSet<>();

    @Option(
            names = {"-f", "--input-file"},
            description = "input file")
    Path inputFile;

    @Option(
            names = {"-o", "--output"},
            description = "Output file (xlsx)")
    Path outputFile;

    @Override
    public Integer call() throws Exception {
        Set<Stock> stocksFromIsinInput = stockIsins.stream()
                .map(isin -> new Stock("", isin, Optional.empty()))
                .collect(Collectors.toSet());
        Set<Stock> stocksFromFileInput = new HashSet<>();

        if (inputFile != null) {
            try (InputStream fileInputStream = Files.newInputStream(inputFile, StandardOpenOption.READ);
                    var json = Json.createReader(fileInputStream)) {
                JsonObject root = json.readObject();
                JsonArray results = root.getJsonArray("results");
                for (JsonValue result : results) {
                    if (!JsonValue.ValueType.OBJECT.equals(result.getValueType())) {
                        continue;
                    }

                    JsonObject stockObject = result.asJsonObject();

                    Stock stock = new Stock(
                            stockObject.getString("name"),
                            stockObject.getString("isin"),
                            Optional.ofNullable(stockObject.getString("index")));

                    stocksFromFileInput.add(stock);
                }
            }
        }

        var allStocks = Stream.concat(stocksFromIsinInput.stream(), stocksFromFileInput.stream())
                .collect(Collectors.toSet());

        try (var scrapeService = new ScrapeService()) {
            List<AktienfinderStock> ratings = scrapeService.scrapeAll(allStocks);

            ExportService exportService = new ExportService();
            exportService.export(ratings, outputFile);
        }

        return 0;
    }
}
