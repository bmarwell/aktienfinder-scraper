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

import de.bmarwell.aktienfinder.scraper.library.download.DownloadListService;
import de.bmarwell.aktienfinder.scraper.library.download.StockDownloadOption;
import de.bmarwell.aktienfinder.scraper.library.download.StockIndex;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to download a list of stocks from predefined indexes and generate a JSON output file.
 * <p>
 * This command downloads stock data from several stock indexes and generates a JSON file with the details.
 * It makes use of the {@link DownloadListService} to perform the stock data retrieval.
 *
 * <p>
 * Options:
 * <ul>
 *   <li>{@code -o}, {@code --output}: Specifies the output file (in JSON format) where the downloaded stock data will be stored.</li>
 * </ul>
 *
 * <p>
 * The downloaded stock data includes:
 * <ul>
 *   <li>Stock name</li>
 *   <li>Stock ISIN</li>
 *   <li>Stock index (if available)</li>
 * </ul>
 *
 * <p>
 * Exit Codes:
 * <ul>
 *   <li>{@code 0}: Successfully completed the download and generated the JSON file.</li>
 *   <li>{@code 1}: No stocks were downloaded.</li>
 * </ul>
 */
@Command(name = "download", header = "Downloads a list of stocks and generates a JSON output file.")
public class DownloadCommand implements Callable<Integer> {

    @Option(
            names = {"-o", "--output"},
            description = "Output file (json)")
    Path outputFile;

    @Override
    public Integer call() throws Exception {
        Set<Stock> stockSet = new LinkedHashSet<>();

        try (DownloadListService downloadListService = new DownloadListService()) {
            var indexes = List.of(StockIndex.values());
            StockDownloadOption stockDownloadOption = new StockDownloadOption(indexes, 9999);
            List<Stock> stocks = downloadListService.downloadStocks(stockDownloadOption);

            stockSet.addAll(stocks);
        }

        if (stockSet.isEmpty()) {
            return 1;
        }

        try (OutputStream outputStream = Files.newOutputStream(
                        outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                JsonWriter jsonWriter = Json.createWriter(outputStream)) {
            JsonArrayBuilder results = Json.createArrayBuilder();

            for (Stock stock : stockSet) {
                JsonObject result = Json.createObjectBuilder()
                        .add("name", Json.createValue(stock.name()))
                        .add("isin", Json.createValue(stock.isin()))
                        .add("index", Json.createValue(stock.index().orElse("")))
                        .build();
                results.add(result);
            }

            JsonObject result =
                    Json.createObjectBuilder().add("results", results.build()).build();

            jsonWriter.writeObject(result);
        }

        return 0;
    }
}
