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
import de.bmarwell.aktienfinder.scraper.library.export.MsExcelExportService;
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

/**
 * This class represents a command-line utility designed to scrape stock information from the
 * website <a href="https://aktienfinder.net">aktienfinder.net</a>. The command is executed with
 * the name {@code scrape} and is intended to collect data on stocks based on input criteria
 * provided by the user.
 *
 * <p>{@code ScrapeCommand} implements the {@link Callable} interface and overrides its
 * {@code call} method to perform the scraping operation. Command-line options are used to
 * specify input ISINs, an input file, and an output file.
 *
 * <p>Options:
 * <ul>
 *     <li>{@code -i, --stocks}: Specifies input stock ISINs with a comma-separated list.</li>
 *     <li>{@code -f, --input-file}: Specifies the input file containing stock data in JSON format.</li>
 *     <li>{@code -o, --output}: Specifies the output file where the scraped stock data will be
 *     exported in an XLSX format.</li>
 * </ul>
 *
 * <p>The {@code call} method:
 * <ol>
 *     <li>Loads stock data from the provided ISINs.</li>
 *     <li>Loads additional stock data from the provided input file if available.</li>
 *     <li>Combines the stock data from both sources into a single set.</li>
 *     <li>Uses {@link ScrapeService} to scrape detailed stock information.</li>
 *     <li>Exports the scraped data to the specified output file using {@link MsExcelExportService}.</li>
 * </ol>
 */
@Command(name = "scrape", header = "Scrapes stocks from aktienfinder.net")
public class ScrapeCommand implements Callable<Integer> {

    @Option(
            names = {"-i", "--stocks"},
            description = "input stock isins",
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

            MsExcelExportService msExcelExportService = new MsExcelExportService();
            msExcelExportService.export(ratings, outputFile);
        }

        return 0;
    }
}
